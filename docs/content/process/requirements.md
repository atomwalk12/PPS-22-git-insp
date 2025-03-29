---
title: "Requirements Engineering"
author: "Razvan"
date: 2025-03-27
description: "Defining the requirements for the Git Inspector project"
aliases: ["requirements"]
tocopen: true
tags: ["requirements"]
summary: "The requirements engineering step where we define all 5 requirement types: Business, Functional (user and system), Non-Functional, Implementation."
bibFile: /assets/bibliography.json
useCitationFooter: true
---

<!-- markdownlint-disable MD051 -->

## Workflow

![PPS Workflow](../../static/figures/PPS-workflow.svg)

## Requirements Engineering

### Requirements Definition

For more details on the requirements engineering process, please refer to [day 4](sprint1/daily_updates/2025-03-27.md) of the first sprint.

{{< insert-text "requirements.md" >}}

Hardware specification:

- CPU: AMD Radeon 7950X3D
- RAM: 64GB
- Storage: SSD

### Domain Model

#### Class Diagram

The domain model is a diagram that shows the relationships between the different entities in the system.

![Class Diagram](../../static/figures/PPS-domain-model-class-diagram.svg)

#### Sequence Diagram

Figure [fig:sequence-diagram-indexing](#fig:sequence-diagram-indexing) shows the interactions between the different entities in the system.

{{< numbered-figure id="fig:sequence-diagram-indexing" align="center" src="../../static/figures/PPS-domain-model-sequence-diagram-indexing.svg" caption="Sequence Diagram that illustrates the repository indexing process" >}}


Figure [fig:sequence-diagram-chat](#fig:sequence-diagram-chat) shows the sequence diagram for the code search process.

{{< numbered-figure id="fig:sequence-diagram-chat" align="center" src="../../static/figures/PPS-domain-model-sequence-diagram-chat.svg" caption="Sequence Diagram that illustrates the code search process" >}}

### Design choices

- [TODO] Gradio picture for the chat interface, annotated to reference the different requirements listed above.

### Requirements Specification

Below, I refine the requirements presented in Section [Requirements Definition](#requirements-definition).

{{< insert-text "requirement-specifications.md" >}}

### Code References

- The requirements should be referenced in the code comments.

```scala
// @Requirement(id = "FR1.1", description = "As a user, I can specify a Git repository URL to inspect its code.")
def inspectGitRepository(url: String): List[Requirement] = ???
```

### Traceability Matrix

[TODO] To trace the requirements to the design elements, I will use a traceability matrix. This matrix will be
populated by running unit tests, which will populate a table with the following format:

<!-- markdownlint-disable MD033 -->
<table style="display: table;">
  <thead>
    <tr>
      <th>Requirement</th>
      <th>Design element</th>
      <th>Implementation Evidence</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>FR1.1</td>
      <td>inspectGitRepository</td>
      <td>inspectGitRepository.scala</td>
    </tr>
    <tr>
      <td>FR1.2</td>
      <td>searchCode</td>
      <td>searchCode.scala</td>
    </tr>
    <tr>
      <td>FR2.1</td>
      <td>buildVectorDatabase</td>
      <td>buildVectorDatabase.scala</td>
    </tr>
    <tr>
      <td>FR2.2</td>
      <td>optimizeSearch</td>
      <td>optimizeSearch.scala</td>
    </tr>
  </tbody>
</table>

## Design

### Architectural Diagram

The architectural diagram is a diagram that shows the different components of the system and their interactions. See sprint 1, [day 3](sprint1/daily_updates/2025-03-26.md) for more details.

![Architectural Diagram](../../static/figures/PPS-architecture.svg)

### Detailed Design

Detailed design includes more implementation specifics like private methods, framework-specific elements, etc.

{{< cite "DNN1" >}}
{{< bibliography cited >}}
