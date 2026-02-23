# Week 4


# Completed tasks

| Task | Weight | Assignee | 
|[#50 Restructure Project for Phase 2] (https://github.com/CS7580-SEA-SP26/d-team/issues/50) | Large |  @Lijun Wan | 
|[#49 Design DB schema and implement data access layer] (https://github.com/CS7580-SEA-SP26/d-team/issues/49) |     Medium   |     @Lijun Wan       |
| [#53 [REST] Implement pipeline execution with DB persistence ](https://github.com/CS7580-SEA-SP26/d-team/issues/53) | Large| @Yange Wang |
| [#54 [REST] Implement report GET endpoints](https://github.com/CS7580-SEA-SP26/d-team/issues/54) | Medium | @yange |
| [#55 [REST] Define DAO interfaces and data model classes ](https://github.com/CS7580-SEA-SP26/d-team/issues/55) | Medium | @yange wang |
| [#60 Format report output in YAML-like Style matching Spec](https://github.com/CS7580-SEA-SP26/d-team/issues/60) | Small | @Molly Yang |
| [#59 Call REST API endpoints and prase JSON response](https://github.com/CS7580-SEA-SP26/d-team/issues/59) | Small | @Molly Yang |
| [#58 Validate Option Combinations in report subcommand](https://github.com/CS7580-SEA-SP26/d-team/issues/58) | Small | @Molly Yang |
| [#57 Add report subcommand with --pipeline, --run,--stage,--job Options](https://github.com/CS7580-SEA-SP26/d-team/issues/57) | Medium | @Molly Yang |
# Carry over tasks

| Task | Weight | Assignee |
| [#44 [Design] Revise Architecture to message queue-driven design| Large(https://github.com/CS7580-SEA-SP26/d-team/issues/44) | @Molly Yang |
|[#45 [Chore] Fix code quality warnings (Checkstyle, SpotBugs, Javadoc)] (https://github.com/CS7580-SEA-SP26/d-team/issues/45) |    Medium    |    @Yange Wang      |

# What worked this week?

> Splitting work by layer (DB → REST → CLI) let Yange and Lijun work in parallel. Lijun merged the DB schema early so Yange could integrate without waiting. 
Defining DAO interfaces upfront kept everyone unblocked.

# What did not work this week?

> JSON response format wasn't agreed on early enough. We had to fix field names late to match the spec. 
> We Should have defined the API contract on Day 1 so everyone can start their own work earlier.  

# Design updates

> Added database persistence for pipeline execution (pipeline_runs, stage_runs, job_runs tables)
> Added 4 REST GET endpoints for report subcommand
> Fixed JSON field names to match project spec (run-no, git-hash, git-branch, git-repo)

