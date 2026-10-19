# Archived — A2A v1 (superseded)

This folder contains the v1 design and action plan. It is kept for historical context
only.

**Do not implement v1.** It re-implements protocol behavior that the official A2A Java
SDK already owns (`A2ARequestDispatcher`, task lifecycle, event queue, JSON-RPC
controllers, server executor ports). The current direction is
[`../a2a-design-v2.md`](../a2a-design-v2.md) and
[`../v2-actions/`](../v2-actions/).

The v1 implementation code under `ai/a2a/` is being cleaned up in task `A2A2-01`; the
migration map is in design v2 §10.
