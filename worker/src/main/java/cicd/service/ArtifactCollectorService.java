package cicd.service;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Matches artifact patterns against the workspace filesystem
 * and returns relative paths of matched files.
 *
 * <p>Supported patterns:
 * <ul>
 *   <li>Exact file: {@code README.md}</li>
 *   <li>Directory (trailing /): {@code build/} — all contents recursively</li>
 *   <li>Single wildcard: {@code *.java}, {@code build/&#42;/doc/}</li>
 *   <li>Double wildcard: {@code build/&#42;&#42;/distribution}</li>
 * </ul>
 */
@Service
public class ArtifactCollectorService {

  private static final Logger log =
      LoggerFactory.getLogger(ArtifactCollectorService.class);

  /**
   * Collects files matching the given patterns under workspaceRoot.
   *
   * @param patterns      artifact pattern strings from the YAML config
   * @param workspaceRoot root directory to resolve patterns against
   * @return list of matched file paths relative to workspaceRoot
   */
  public List<String> collect(List<String> patterns, Path workspaceRoot) {
    List<String> matched = new ArrayList<>();
    if (patterns == null || patterns.isEmpty()) {
      return matched;
    }

    for (String pattern : patterns) {
      try {
        List<String> files = matchPattern(pattern, workspaceRoot);
        matched.addAll(files);
        log.debug("Pattern '{}' matched {} file(s)", pattern, files.size());
      } catch (IOException ex) {
        log.warn("Failed to match pattern '{}': {}", pattern, ex.getMessage());
      }
    }

    return matched;
  }

  private List<String> matchPattern(String pattern, Path root)
      throws IOException {
    List<String> result = new ArrayList<>();

    // If pattern contains wildcards, use glob matching
    if (pattern.contains("*")) {
      return matchWildcardPattern(pattern, root);
    }

    // Directory pattern (trailing /) without wildcards
    if (pattern.endsWith("/")) {
      Path dir = root.resolve(pattern.substring(0, pattern.length() - 1));
      if (Files.isDirectory(dir)) {
        collectAllFiles(dir, root, result);
      }
      return result;
    }

    // Exact file or directory
    Path target = root.resolve(pattern);
    if (Files.isRegularFile(target)) {
      result.add(root.relativize(target).toString());
    } else if (Files.isDirectory(target)) {
      collectAllFiles(target, root, result);
    }

    return result;
  }

  private List<String> matchWildcardPattern(String pattern, Path root)
      throws IOException {
    List<String> result = new ArrayList<>();

    // If pattern ends with /, match directories and collect all contents
    boolean matchDirOnly = pattern.endsWith("/");
    // For directory glob matching, strip trailing / so glob matches
    // the directory name itself
    String globStr = matchDirOnly
        ? pattern.substring(0, pattern.length() - 1) : pattern;
    PathMatcher matcher =
        FileSystems.getDefault().getPathMatcher("glob:" + globStr);
    // Java glob ** requires at least one path segment. Create a
    // secondary matcher with **/ removed to handle the zero-depth case
    // e.g. build/**/distribution should also match build/distribution
    PathMatcher zeroDepthMatcher = globStr.contains("**/")
        ? FileSystems.getDefault().getPathMatcher(
            "glob:" + globStr.replace("**/", ""))
        : null;

    Files.walkFileTree(root, new SimpleFileVisitor<>() {
      private boolean matches(Path relative) {
        return matcher.matches(relative)
            || (zeroDepthMatcher != null
                && zeroDepthMatcher.matches(relative));
      }

      @Override
      public FileVisitResult visitFile(Path file,
          BasicFileAttributes attrs) {
        if (!matchDirOnly) {
          Path relative = root.relativize(file);
          if (matches(relative)) {
            result.add(relative.toString());
          }
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult preVisitDirectory(Path dir,
          BasicFileAttributes attrs) {
        if (dir.equals(root)) {
          return FileVisitResult.CONTINUE;
        }
        Path relative = root.relativize(dir);
        if (matches(relative)) {
          // Pattern matches a directory — collect all its contents
          try {
            collectAllFiles(dir, root, result);
          } catch (IOException ex) {
            log.warn("Error collecting dir '{}': {}",
                relative, ex.getMessage());
          }
          return FileVisitResult.SKIP_SUBTREE;
        }
        return FileVisitResult.CONTINUE;
      }
    });

    return result;
  }

  private void collectAllFiles(Path dir, Path root, List<String> result)
      throws IOException {
    Files.walkFileTree(dir, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file,
          BasicFileAttributes attrs) {
        result.add(root.relativize(file).toString());
        return FileVisitResult.CONTINUE;
      }
    });
  }
}
