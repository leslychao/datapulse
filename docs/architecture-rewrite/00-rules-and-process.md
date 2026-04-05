# 00 Rules and Process — Rewrite from Scratch

## Purpose

This document defines the rules, principles, and process for the Datapulse architecture rewrite. It is the governing contract for all documents in `/docs/architecture-rewrite/`.

---

## A. Foundational Principles

### 1. Existing code is a knowledge source, not architectural truth

The current codebase contains real business logic, real invariants, and real contracts — but it also contains historical layering, accidental complexity, and workaround logic. The goal of the diagnostic phase is to **separate these two categories** before any new design begins.

We treat the legacy code as an **evidence base** — something to be studied, questioned, and verified — not as a blueprint to copy.

### 2. Preserve business meaning, not implementation structure

What matters and must be preserved:
- Business invariants (e.g., "price action is not SUCCEEDED until reconciliation confirms it")
- External contracts (marketplace API shapes, credential flows, rate limits)
- Data semantics (canonical model meanings, P&L formula, sign conventions)
- Lifecycle guarantees (state machines, at-least-once delivery, audit trail)
- Security boundaries (workspace isolation, RBAC, credential management)

What does NOT need to be preserved:
- Current package structure
- Current class hierarchy
- Current layering decisions
- Current naming conventions
- Current technology choices (unless they are load-bearing)

### 3. Do not preserve historical layering by default

The current architecture has accumulated layers over time. Some layers exist because of genuine architectural need. Others exist because of incremental evolution, copy-paste patterns, or framework conventions. The rewrite must evaluate each layer on its own merits.

### 4. Do not design the new architecture prematurely

This diagnostic phase is about **extraction and understanding**. We explicitly do NOT:
- Design new module boundaries
- Choose new technology stacks
- Propose new patterns
- Write production code
- Refactor existing code

These activities belong to a later phase, after the diagnostic is complete and validated.

---

## B. Document Hierarchy

All documents in `/docs/architecture-rewrite/` are organized by number prefix:

| File | Purpose | Status |
|------|---------|--------|
| `00-rules-and-process.md` | This file. Governing rules and process. | Active |
| `01-current-system-analysis.md` | What the system IS today (structure, flows, boundaries) | Active |
| `02-business-model-extracted.md` | What the system DOES (business capabilities, invariants, rules) | Active |
| `03-legacy-problems-and-noise.md` | What is WRONG or unnecessary (defects, noise, accidental complexity) | Active |
| `04-rewrite-invariants-and-constraints.md` | What MUST NOT be lost (non-negotiable requirements for rewrite) | Active |
| `10-progress-tracker.md` | Current status of the diagnostic process | Active |

Future documents (05+) may be added for architecture proposals, migration plans, etc. — but only after the diagnostic phase (01-04) is complete and validated.

---

## C. Process Rules

### Updates are always in-place

Every document is updated by modifying the existing file. No alternative copies, no renamed files, no parallel versions. Git history provides the version trail.

### Contradictions are resolved explicitly

If two documents contradict each other, the contradiction must be resolved in the relevant document(s) with an explicit note:
- What the contradiction was
- How it was resolved
- Why

### Uncertainty is always marked

Every document has a "Confidence / Uncertainties" section. When a conclusion is not fully confirmed by code evidence, it must be flagged with:
- `[CONFIRMED]` — verified by reading code, database schema, or tests
- `[HIGH CONFIDENCE]` — strong evidence from multiple sources, but not 100% verified
- `[UNCERTAIN]` — plausible interpretation, but alternative explanations exist
- `[UNKNOWN]` — insufficient evidence to draw a conclusion

### No code in diagnostic documents

These documents are architectural analysis artifacts. Code snippets are acceptable only as evidence (short quotes proving a point). No production code, no proposed implementations, no pseudocode designs.

### Progress tracker is always current

`10-progress-tracker.md` must be updated whenever any other document changes. It reflects what has been analyzed, what remains, and what the key findings are so far.

---

## D. Analysis Principles

### Separate fact from interpretation

When analyzing code, distinguish between:
- **Fact:** "PricingRunService.executeRun() is annotated @Transactional" — objectively verifiable
- **Interpretation:** "The entire pricing run operates in a single large transaction, which may cause lock contention" — a judgment call

Both are valuable, but they must be clearly distinguished.

### Trace execution flows, not just structure

Static structure (packages, classes, inheritance) tells you what was intended. Execution flows (what actually gets called, in what order, with what data) tell you what actually happens. Prefer execution flow analysis over structural analysis.

### Follow the data, not the abstraction

Data flows (what gets written where, what gets read from where, what transformation happens) are more architecturally significant than abstraction layers. When in doubt, trace the data.

### Question every boundary

For each module boundary, ask:
- Does this boundary exist because of a genuine business concern?
- Or does it exist because of historical accident / framework convention / copy-paste?
- What would happen if this boundary didn't exist?
- What would happen if this boundary were drawn differently?

---

## E. Definitions

| Term | Meaning in this context |
|------|------------------------|
| **Business invariant** | A rule that must always be true, regardless of implementation. Example: "a pricing decision always references the policy version that produced it." |
| **External contract** | An interface with a system outside our control. Example: Ozon Seller API, Wildberries Statistics API. |
| **Accidental complexity** | Complexity that exists because of implementation choices, not business requirements. |
| **Historical layering** | Abstractions or indirections that were added incrementally and may no longer serve their original purpose. |
| **Legacy noise** | Code, configuration, or structure that is no longer necessary but hasn't been removed. |
| **Source of truth** | The authoritative store for a piece of data. For Datapulse: PostgreSQL for business state, S3 for raw marketplace responses, ClickHouse for analytical aggregates. |
