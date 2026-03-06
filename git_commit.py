import subprocess, os

def run(cmd, cwd=None):
    print(f">>> {cmd}")
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd=cwd, timeout=300)
    if r.stdout: print(r.stdout.strip())
    if r.stderr: print("STDERR:", r.stderr.strip())
    print(f"Exit: {r.returncode}\n")
    return r.returncode

W = r"C:\Users\adam-\GitHub\StarlitCoffee-litert"
M = r"C:\Users\adam-\GitHub\StarlitCoffee"
MSG = """feat: replace ML Kit GenAI with LiteRT-LM for on-device AI extraction

- Remove Gemini Nano (ML Kit GenAI) - only supported ~15 flagship devices
- Add LiteRT-LM with Gemma 3n E2B - supports any device with 4GB+ RAM
- Add ModelManager for model download/lifecycle (1.5GB on-demand)
- Add LiteRtLabelExtractor implementing AiLabelExtractor interface
- Add AI settings section with download progress and model management
- Wire AI extraction into OCR pipeline in BagInventoryScreen
- Update PromptTemplates for LiteRT-LM Conversation API

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"""

# Clean locks
for f in [os.path.join(M,".git","worktrees","StarlitCoffee-litert","index.lock"), os.path.join(M,".git","index.lock")]:
    if os.path.exists(f): os.remove(f); print(f"Removed {f}")

# Stage + commit worktree
run("git add -A", W)
mf = os.path.join(W, "COMMIT_MSG.txt")
open(mf,"w",encoding="utf-8").write(MSG)
rc = run("git commit --no-gpg-sign -F COMMIT_MSG.txt", W)
os.remove(mf)
run("git --no-pager log --oneline -3", W)
if rc != 0: print("Commit failed"); exit(1)

# Stash main, merge, commit, pop
run("git stash --include-untracked", M)
rc = run("git merge --squash feature/litert-lm-migration", M)
if rc != 0: run("git stash pop", M); print("Merge failed"); exit(1)
mf2 = os.path.join(M, "COMMIT_MSG.txt")
open(mf2,"w",encoding="utf-8").write(MSG)
run("git commit --no-gpg-sign -F COMMIT_MSG.txt", M)
os.remove(mf2)
run("git stash pop", M)

# Cleanup
run(f'git worktree remove "{W}" --force', M)
run("git branch -D feature/litert-lm-migration", M)
run("git --no-pager log --oneline -5", M)
print("DONE")
