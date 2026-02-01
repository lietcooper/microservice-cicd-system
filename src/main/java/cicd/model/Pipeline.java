package cicd.model;

import java.util.*;

public class Pipeline {
    public String name;
    public String desc;
    public List<String> stages = new ArrayList<>();
    public Map<String, Job> jobs = new LinkedHashMap<>();
    
    public int nameLine = -1;
    public int nameCol = -1;
}
