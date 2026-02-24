
# Week 4

##  Completed 

| Task                                                                                                                                    | Weight | Assignee    |
| --------------------------------------------------------------------------------------------------------------------------------------- | ------ | ----------- |
| [#50 Restructure Project for Phase 2](https://github.com/CS7580-SEA-SP26/d-team/issues/50)                                              | Large  | @Lijun Wan  |
| [#49 Design DB Schema and Implement Data Access Layer](https://github.com/CS7580-SEA-SP26/d-team/issues/49)                             | Medium | @Lijun Wan  |
| [#44 Revise Architecture to Message Queue–Driven Design](https://github.com/CS7580-SEA-SP26/d-team/issues/44)                           | Large  | @Lijun Wan  |
| [#53 Implement Pipeline Execution with DB Persistence](https://github.com/CS7580-SEA-SP26/d-team/issues/53)                             | Large  | @Yange Wang |
| [#54 Implement Report GET Endpoints](https://github.com/CS7580-SEA-SP26/d-team/issues/54)                                               | Medium | @Yange Wang |
| [#55 Define DAO Interfaces and Data Model Classes](https://github.com/CS7580-SEA-SP26/d-team/issues/55)                                 | Medium | @Yange Wang |
| [#60 Format Report Output in YAML-like Style Matching Spec](https://github.com/CS7580-SEA-SP26/d-team/issues/60)                        | Small  | @Molly Yang |
| [#59 Call REST API Endpoints and Parse JSON Response](https://github.com/CS7580-SEA-SP26/d-team/issues/59)                              | Small  | @Molly Yang |
| [#58 Validate Option Combinations in Report Subcommand](https://github.com/CS7580-SEA-SP26/d-team/issues/58)                            | Small  | @Molly Yang |
| [#57 Add Report Subcommand with `--pipeline`, `--run`, `--stage`, `--job` Options](https://github.com/CS7580-SEA-SP26/d-team/issues/57) | Medium | @Molly Yang |

##  Carry-Over 

| Task                                                                                                                 | Weight | Assignee    |
| -------------------------------------------------------------------------------------------------------------------- | ------ | ----------- |
| [#45 Fix Code Quality Warnings (Checkstyle, SpotBugs, Javadoc)](https://github.com/CS7580-SEA-SP26/d-team/issues/45) | Medium | @Yange Wang |


## What Worked

* Splitting work by layer (**DB → REST → CLI**) allowed parallel development.
* The DB schema was merged early, enabling smooth integration.
* Defining DAO interfaces upfront reduced blocking and improved coordination.


##  What Didn’t Work Well

* The JSON response format was not agreed upon early.
* Field names had to be adjusted late to match the project specification.
* The API contract should have been defined on Day 1 to avoid rework.


##  Design Updates

* Added database persistence for pipeline execution:

    * `pipeline_runs`
    * `stage_runs`
    * `job_runs`
* Added 4 REST GET endpoints to support the report subcommand.
* Updated JSON field names to match project spec:

    * `run-no`
    * `git-hash`
    * `git-branch`
    * `git-repo`
* Replaced the old high-level design Mermaid diagram with the new **message queue–driven design**.

