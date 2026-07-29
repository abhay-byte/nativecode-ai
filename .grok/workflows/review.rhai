let meta = #{
    name: "review",
    description: "Review branch code against an attached plan.md, write findings into the plan, bump X Interaction iteration + mark X review done, emit a copy-paste handoff prompt for a worker agent, then stop",
    when_to_use: "After impl on a branch. Pass plan path. Optional user testing findings. Does not fix code — only reviews and updates the plan, then hands off.",
    phases: [
        #{ title: "Load", detail: "Resolve plan path, branch, base, user findings" },
        #{ title: "Context", detail: "Read plan + git branch diff" },
        #{ title: "Review", detail: "Parallel dimension reviews vs plan" },
        #{ title: "Update plan", detail: "Write findings, X Interaction, X review done" },
        #{ title: "Handoff", detail: "Worker agent copy-paste prompt; stop" },
    ],
};

let context_schema = #{
    "type": "object",
    "required": ["plan_path", "branch", "base", "plan_summary", "diff_summary", "iteration_current", "user_findings_present"],
    "properties": #{
        "plan_path": #{ "type": "string" },
        "branch": #{ "type": "string" },
        "base": #{ "type": "string" },
        "plan_summary": #{ "type": "string" },
        "diff_summary": #{ "type": "string" },
        "iteration_current": #{ "type": "integer" },
        "user_findings_present": #{ "type": "boolean" },
        "user_findings_excerpt": #{ "type": "string" },
    },
};

let findings_schema = #{
    "type": "object",
    "required": ["dimension", "verdict", "findings"],
    "properties": #{
        "dimension": #{ "type": "string" },
        "verdict": #{ "type": "string" },
        "findings": #{
            "type": "array",
            "maxItems": 12,
            "items": #{
                "type": "object",
                "required": ["severity", "file", "issue", "fix_hint"],
                "properties": #{
                    "severity": #{ "type": "string" },
                    "file": #{ "type": "string" },
                    "issue": #{ "type": "string" },
                    "fix_hint": #{ "type": "string" },
                },
            },
        },
    },
};

let update_schema = #{
    "type": "object",
    "required": ["plan_path", "iteration", "review_done", "findings_count", "summary", "worker_prompt"],
    "properties": #{
        "plan_path": #{ "type": "string" },
        "iteration": #{ "type": "integer" },
        "review_done": #{ "type": "boolean" },
        "findings_count": #{ "type": "integer" },
        "summary": #{ "type": "string" },
        "worker_prompt": #{ "type": "string" },
    },
};

fn trimmed(s) {
    if type_of(s) == "string" {
        s.trim();
        s
    } else {
        ""
    }
}

let plan_path = ();
let branch = "";
let base = "";
let user_findings = "";
let skip_await = false;

if args != () {
    if args.plan != () { plan_path = args.plan; }
    if args.plan_path != () { plan_path = args.plan_path; }
    if args.branch != () { branch = trimmed(args.branch); }
    if args.base != () { base = trimmed(args.base); }
    if args.user_findings != () { user_findings = trimmed(args.user_findings); }
    if args.testing_findings != () {
        let tf = trimmed(args.testing_findings);
        if tf != "" { user_findings = tf; }
    }
    if args.skip_await != () && args.skip_await == true { skip_await = true; }
}

if plan_path == () || trimmed(plan_path) == "" {
    pause("verification", "Pass args.plan or args.plan_path — path to the plan.md to review against.");
}
plan_path = trimmed(plan_path);

if base == "" { base = "master"; }

if user_findings == "" && !skip_await {
    await_user(
        "user",
        "Optional: add device/manual testing findings now. 1) Edit plan under ## User Testing Findings then resume. 2) Re-run with args.user_findings. 3) Resume with no findings (code-only). Pass skip_await:true to skip this gate."
    );
}

phase("Load");
log("plan=" + plan_path + " base=" + base);

phase("Context");
let ctx_prompt = "";
ctx_prompt += "You are the context loader for a plan-vs-branch review workflow. Use tools. Do not invent.\n\n";
ctx_prompt += "PLAN PATH: " + plan_path + "\n";
ctx_prompt += "PREFERRED BRANCH ARG: " + branch + " (empty means detect current git branch)\n";
ctx_prompt += "BASE BRANCH: " + base + "\n";
ctx_prompt += "USER FINDINGS ARG (may be empty):\n" + user_findings + "\n\n";
ctx_prompt += "REQUIRED ACTIONS:\n";
ctx_prompt += "1. Read the plan file at PLAN PATH fully.\n";
ctx_prompt += "2. Run git: status, branch --show-current, merge-base with BASE, git diff BASE...HEAD --stat, and a bounded git diff.\n";
ctx_prompt += "3. Detect current ## X Interaction / Iteration value in the plan (look for Iteration:). If missing, iteration_current=0.\n";
ctx_prompt += "4. Collect user testing findings from: (a) USER FINDINGS ARG, and/or (b) plan section ## User Testing Findings. Set user_findings_present; put short excerpt in user_findings_excerpt.\n";
ctx_prompt += "5. plan_summary: 5-12 lines of what the plan says should be implemented.\n";
ctx_prompt += "6. diff_summary: files changed + high-level what the branch did.\n";
ctx_prompt += "7. Return ONLY the schema object. Always fill plan_summary and diff_summary after reading.\n";

let ctx = agent(ctx_prompt, #{
    label: "load-context",
    capability_mode: "execute",
    output_schema: context_schema,
});

if ctx == () || !ctx.success || ctx.output == () {
    pause("infra", "Context loader failed. Fix git/plan path and resume or re-run.");
}

let plan_abs = ctx.output.plan_path;
let resolved_branch = ctx.output.branch;
let resolved_base = ctx.output.base;
let plan_summary = ctx.output.plan_summary;
let diff_summary = ctx.output.diff_summary;
let iter0 = ctx.output.iteration_current;
if type_of(iter0) != "i64" && type_of(iter0) != "int" && type_of(iter0) != "f64" {
    iter0 = 0;
}
let user_findings_present = ctx.output.user_findings_present == true;
let user_findings_excerpt = "";
if ctx.output.user_findings_excerpt != () {
    user_findings_excerpt = trimmed(ctx.output.user_findings_excerpt);
}

log("branch=" + resolved_branch + " base=" + resolved_base + " iter=" + iter0.to_string() + " user_findings=" + user_findings_present.to_string());

phase("Review");

let dimensions = [
    "plan adherence (did code implement the plan? missed items? scope creep?)",
    "correctness (logic bugs, error handling, edge cases, Android lifecycle)",
    "security (secrets, injection, permissions, path traversal, unsafe intents)",
    "tests and user-reported testing findings (gaps vs plan verification; incorporate user testing notes)",
];

let jobs = [];
for d in dimensions {
    let p = "";
    p += "You are a strict code reviewer. USE TOOLS — read plan file, git diff, and actual source. Do not answer from memory.\n\n";
    p += "PLAN PATH: " + plan_abs + "\n";
    p += "BRANCH: " + resolved_branch + "\n";
    p += "BASE: " + resolved_base + "\n";
    p += "DIMENSION: " + d + "\n\n";
    p += "PLAN SUMMARY:\n" + plan_summary + "\n\n";
    p += "DIFF SUMMARY:\n" + diff_summary + "\n\n";
    p += "USER TESTING FINDINGS PRESENT: " + user_findings_present.to_string() + "\n";
    p += "USER TESTING EXCERPT:\n" + user_findings_excerpt + "\n";
    if user_findings != "" {
        p += "USER FINDINGS ARG:\n" + user_findings + "\n";
    }
    p += "\nINSTRUCTIONS:\n";
    p += "1. Read the plan file.\n";
    p += "2. Inspect git diff " + resolved_base + "...HEAD and open relevant source files.\n";
    p += "3. For this DIMENSION only, list concrete findings (max 12). severity one of: blocker, major, minor, nit.\n";
    p += "4. Every finding needs file path (or plan section), issue, and fix_hint.\n";
    p += "5. If reviewing tests/user findings dimension: treat user testing notes as first-class evidence; map each to code root cause when possible.\n";
    p += "6. verdict: one short line (e.g. CHANGES_REQUESTED / LOOKS_GOOD / BLOCK).\n";
    p += "7. Empty findings list is valid ONLY after you actually inspected the code for this dimension.\n";
    p += "8. dimension field must equal the DIMENSION string given above.\n";
    jobs.push(#{
        prompt: p,
        label: "review-dim",
        capability_mode: "execute",
        output_schema: findings_schema,
    });
}

let review_results = parallel(jobs);

let all_findings_text = "";
let finding_count = 0;
let i = 0;
for r in review_results {
    if r != () && r.success && r.output != () {
        let dim = "";
        if r.output.dimension != () { dim = r.output.dimension; }
        let verd = "";
        if r.output.verdict != () { verd = r.output.verdict; }
        all_findings_text += "### Dimension: " + dim + "\n";
        all_findings_text += "Verdict: " + verd + "\n\n";
        if r.output.findings != () {
            for f in r.output.findings {
                finding_count += 1;
                let sev = "";
                let file = "";
                let issue = "";
                let fix = "";
                if f.severity != () { sev = f.severity; }
                if f.file != () { file = f.file; }
                if f.issue != () { issue = f.issue; }
                if f.fix_hint != () { fix = f.fix_hint; }
                all_findings_text += "- [" + sev + "] " + file + ": " + issue + " | fix: " + fix + "\n";
            }
        }
        all_findings_text += "\n";
    } else {
        log("review slot " + i.to_string() + " failed or empty — continuing with other dimensions");
    }
    i += 1;
}

log("raw finding rows=" + finding_count.to_string());

if all_findings_text == "" {
    all_findings_text = "(No structured findings returned by dimension reviewers. Treat as incomplete review — worker should re-check plan vs diff.)\n";
}

phase("Update plan");

let next_iter = iter0 + 1;

let upd = "";
upd += "You update the plan markdown file in place. Use write tools. Do NOT change application source code.\n\n";
upd += "PLAN PATH (must write this exact file): " + plan_abs + "\n";
upd += "BRANCH: " + resolved_branch + "\n";
upd += "BASE: " + resolved_base + "\n";
upd += "NEXT ITERATION NUMBER: " + next_iter.to_string() + "\n\n";
upd += "AGGREGATED REVIEW FINDINGS TO RECORD:\n" + all_findings_text + "\n\n";
upd += "USER TESTING FINDINGS PRESENT: " + user_findings_present.to_string() + "\n";
upd += "USER TESTING EXCERPT:\n" + user_findings_excerpt + "\n";
if user_findings != "" {
    upd += "USER FINDINGS ARG:\n" + user_findings + "\n";
}
upd += "\nREQUIRED FILE EDITS on the plan:\n";
upd += "1. Ensure section exactly titled: ## X Interaction\n";
upd += "   Under it, maintain these lines (replace if present, create if missing):\n";
upd += "   - Iteration: " + next_iter.to_string() + "\n";
upd += "   - X review done: yes\n";
upd += "   - Last review branch: " + resolved_branch + "\n";
upd += "   - Last review base: " + resolved_base + "\n";
upd += "2. Ensure section: ## Review Findings\n";
upd += "   Replace previous Review Findings body with a fresh block for this iteration:\n";
upd += "   ### Iteration " + next_iter.to_string() + "\n";
upd += "   Then paste the aggregated findings (include severities).\n";
upd += "   Also list any user testing findings that remain open.\n";
upd += "3. If ## User Testing Findings exists and user provided new arg findings not already there, append them (do not delete old user notes).\n";
upd += "4. Do not delete the original plan body. Only update/append X Interaction + Review Findings (+ optional User Testing Findings).\n";
upd += "5. After writing, re-read the file to confirm Iteration and X review done lines.\n\n";
upd += "Then build worker_prompt: a SINGLE copy-pasteable prompt for a DIFFERENT worker/implementer agent. The prompt must:\n";
upd += "- Tell the worker to open the plan at this exact path: " + plan_abs + "\n";
upd += "- Work on branch " + resolved_branch + " against base " + resolved_base + "\n";
upd += "- Address ALL review findings + remaining user testing findings\n";
upd += "- Follow the original plan intent; no unrelated scope creep\n";
upd += "- After fixes, leave ## X Interaction with X review done: no (so next review can re-check), keep Iteration number\n";
upd += "- Not re-litigate the review; implement fixes\n";
upd += "Put the full worker prompt text in worker_prompt field.\n";
upd += "summary: 2-5 sentences of what is wrong / what worker must do.\n";
upd += "findings_count: integer count of distinct issues written.\n";
upd += "iteration: " + next_iter.to_string() + ", review_done: true, plan_path: absolute path written.\n";

let update_result = agent(upd, #{
    label: "update-plan",
    capability_mode: "read-write",
    output_schema: update_schema,
});

if update_result == () || !update_result.success || update_result.output == () {
    pause("infra", "Plan update agent failed. Plan may be partial. Inspect plan file and resume/re-run.");
}

let out_plan = update_result.output.plan_path;
let out_iter = update_result.output.iteration;
let out_count = update_result.output.findings_count;
let out_summary = update_result.output.summary;
let worker_prompt = update_result.output.worker_prompt;
if out_plan == () || trimmed(out_plan) == "" { out_plan = plan_abs; }
if worker_prompt == () { worker_prompt = ""; }
if out_summary == () { out_summary = ""; }

phase("Handoff");

let report = "";
report += "# Review workflow complete — STOP\n\n";
report += "This workflow does **not** implement fixes. Hand off to a worker agent.\n\n";
report += "## Updated plan\n\n";
report += "- Path: `" + out_plan + "`\n";
report += "- Iteration: " + out_iter.to_string() + "\n";
report += "- X review done: yes\n";
report += "- Findings recorded: " + out_count.to_string() + "\n\n";
report += "## Summary\n\n";
report += out_summary + "\n\n";
report += "## Copy-paste prompt for worker agent\n\n";
report += "```\n";
report += worker_prompt + "\n";
report += "```\n\n";
report += "## Next step for you (human)\n\n";
report += "1. Copy the worker prompt above.\n";
report += "2. Paste into a **different** worker/implementer agent session.\n";
report += "3. After the worker finishes, re-run this review workflow with the same plan path (and any new testing notes).\n";

let scratch = write_scratch_file("review-handoff.md", report);
log("handoff written; plan=" + out_plan);

complete(#{
    plan_path: out_plan,
    iteration: out_iter,
    review_done: true,
    findings_count: out_count,
    summary: out_summary,
    worker_prompt: worker_prompt,
    report_path: scratch,
    stop: true,
    message: "Review done. Plan updated. Copy worker_prompt to a different agent. Workflow stops here.",
});
