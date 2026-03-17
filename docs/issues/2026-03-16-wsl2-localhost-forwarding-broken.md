# WSL2 localhost forwarding and netsh portproxy broken after KB5074829

## Environment

- **Windows Build**: 10.0.29550.1000 (Canary channel)
- **WSL Version**: 2.6.2.0
- **Kernel**: 6.6.87.2-microsoft-standard-WSL2
- **Update**: KB5074829 (installed 2026-03-17)

## Symptoms

1. `localhostForwarding=true` in `.wslconfig` stops working — Windows cannot reach WSL2 services via `localhost:<port>`
2. `netsh interface portproxy` rules are accepted and persist (`show all` lists them) but **no TCP listener is created** on the specified address/port — confirmed via `netstat -an`
3. Direct TCP connection from Windows to WSL2 internal IP (`172.24.x.x`) via `Test-NetConnection` still succeeds — the WSL2 VM itself is reachable, only the forwarding mechanisms are broken

## Reproduction

```powershell
# In WSL2, start any listener:
python3 -m http.server 8080 --bind 0.0.0.0

# From Windows PowerShell:
Invoke-WebRequest http://localhost:8080        # TIMEOUT — should work with localhostForwarding=true
Test-NetConnection 172.24.x.x -Port 8080       # TRUE — direct path works

# Add portproxy:
netsh interface portproxy add v4tov4 listenport=8080 listenaddress=0.0.0.0 connectport=8080 connectaddress=172.24.x.x
netstat -an | Select-String 8080               # EMPTY — no listener created
```

## Attempted fixes (none worked)

- Restart IP Helper service (`Restart-Service iphlpsvc -Force`)
- Delete and re-add portproxy rules
- Add Windows Firewall inbound rule for the port
- Reset all portproxy rules (`netsh interface portproxy reset`) and re-add

## Workaround

Add `networkingMode=mirrored` to `.wslconfig`:

```ini
[wsl2]
networkingMode=mirrored
```

Then `wsl --shutdown` and restart. In mirrored mode WSL2 shares the host network stack — ports opened in WSL2 are directly accessible on all host interfaces without any forwarding.

## Notes

- This is on the Windows Canary build, so the bug may not affect stable/release channels
- Mirrored networking has its own trade-offs (no separate WSL2 IP, some Docker networking differences) but eliminates the need for portproxy entirely
