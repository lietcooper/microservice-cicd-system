package cicd.model;

import java.util.*;

public class Job {
    public String name;
    public String stage;
    public String image;
    public List<String> script = new ArrayList<>();
    public List<String> needs = new ArrayList<>();
    
    public int line = -1;
    public int col = -1;
    public int needsLine = -1;
    public int needsCol = -1;
}
