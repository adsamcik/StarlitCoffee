import subprocess, os
os.chdir(r"C:\Users\adam-\GitHub\StarlitCoffee")

def run(args):
    r = subprocess.run(args, capture_output=True, text=True, timeout=120)
    if r.stdout: print(r.stdout.strip())
    if r.stderr and r.returncode != 0: print("ERR:", r.stderr.strip())
    return r.returncode

print("=== STAGED FILES ===")
run(["git","diff","--cached","--name-only"])

print("\n=== COMMITTING ===")
rc = run(["git","commit","--no-gpg-sign","-m","feat: replace ML Kit GenAI with LiteRT-LM for on-device AI extraction"])

print("\n=== LOG ===")
run(["git","--no-pager","log","--oneline","-3"])

print("\n=== CLEANUP WORKTREE ===")
run(["git","worktree","remove",r"C:\Users\adam-\GitHub\StarlitCoffee-litert","--force"])
run(["git","branch","-D","feature/litert-lm-migration"])

print("\n=== FINAL STATUS ===")
run(["git","--no-pager","status","--short"])
print("\nDONE")
