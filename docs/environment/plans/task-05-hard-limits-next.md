# Task 5 — Remaining hard limits & next experiments

| Field | Value |
|-------|--------|
| **Status** | **PLANNED** |
| **Depends on** | Task 4 results |

---

## 1. Objective

After Tasks 2–4, classify remaining gaps as:

- **Closed** (user-visible OK)
- **Hard limit** (ptrace / kernel)
- **Worth next experiment**

Only propose invasive work if practical gap remains.

---

## 2. Known hard limits (from research)

| Limit | Cause | Mitigation |
|-------|-------|------------|
| Every-syscall ptrace | proot architecture | chroot when root available |
| Many lstat on path resolve | proot path rewrite | shorter paths; fewer binds; lstat cache patch (exp.) |
| Android seccomp → full PTRACE_SYSCALL | zygote filter | hard; selective seccomp exp. |
| Extreme sysbench-mem on some SoCs | device-specific | prefer chroot for heavy RAM |
| dd sequential read ~0.4–0.5× | path + app-data rootfs | chroot; tmpfs hot paths |
| GPU client tax (Turnip sample 0.32×) | ioctl under ptrace | chroot for heavy GL; shader cache |

---

## 3. Next experiments (priority if needed)

| # | Experiment | Benefit | Risk |
|---|------------|---------|------|
| E1 | Shorter rootfs path (bind/symlink under `/data/local/tmp/…`) | fewer path components | SELinux / cleanup |
| E2 | proot lstat cache patch | I/O-heavy | maintenance fork |
| E3 | Re-enable proot seccomp accelerator if safe | less ptrace | Android breakages |
| E4 | Persistent proot “session daemon” | multi-cmd without re-exec | lifecycle bugs |
| E5 | fakechroot / bwrap hybrid where allowed | less ptrace | incomplete Debian |

---

## 4. Decision tree (product)

```text
Need rootless? ──yes──► proot-fast (cli/gpu profile)
       │
       no (KernelSU)
       ▼
Heavy RAM / big compile / many small files / heavy GL?
       │
      yes ──► chroot
       │
      no  ──► proot-fast still OK
```

---

## 5. Improvements log (fill after Task 4)

| Gap | Closed? | Next action |
|-----|---------|-------------|
| — | — | — |

---

## 6. Task-5 regression gates

| ID | Check |
|----|-------|
| T5-R01 | Written recommendation matrix in Task 6 |
| T5-R02 | No experimental patch merged without suite P0 |

**Status:** not run.
