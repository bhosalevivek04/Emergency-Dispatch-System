# Fix Kafka Hostname Resolution for STS

## Problem
Services running in STS try to connect to `kafka:9092` but the hostname "kafka" doesn't resolve to localhost.

## Solution
Add `kafka` to Windows hosts file so it resolves to `127.0.0.1`.

## Manual Steps

### Option 1: Using Notepad (Recommended)

1. **Open Notepad as Administrator:**
   - Press Windows key
   - Type "notepad"
   - Right-click on Notepad
   - Select "Run as administrator"

2. **Open hosts file:**
   - In Notepad, click File → Open
   - Navigate to: `C:\Windows\System32\drivers\etc`
   - Change file filter from "Text Documents (*.txt)" to "All Files (*.*)"
   - Select the file named `hosts` (no extension)
   - Click Open

3. **Add kafka entry:**
   - Scroll to the bottom of the file
   - Add these two lines:
   ```
   # Kafka for local development
   127.0.0.1 kafka
   ```

4. **Save and close:**
   - Click File → Save
   - Close Notepad

### Option 2: Using PowerShell (Administrator)

1. **Open PowerShell as Administrator:**
   - Press Windows key
   - Type "powershell"
   - Right-click on "Windows PowerShell"
   - Select "Run as administrator"

2. **Run these commands:**
   ```powershell
   # Add kafka to hosts file
   Add-Content -Path "C:\Windows\System32\drivers\etc\hosts" -Value "`n# Kafka for local development"
   Add-Content -Path "C:\Windows\System32\drivers\etc\hosts" -Value "127.0.0.1 kafka"
   
   # Verify it was added
   Get-Content "C:\Windows\System32\drivers\etc\hosts" | Select-String "kafka"
   ```

### Option 3: Using Command Prompt (Administrator)

1. **Open Command Prompt as Administrator:**
   - Press Windows key
   - Type "cmd"
   - Right-click on "Command Prompt"
   - Select "Run as administrator"

2. **Run this command:**
   ```cmd
   echo 127.0.0.1 kafka >> C:\Windows\System32\drivers\etc\hosts
   ```

## Verify the Fix

After adding the entry, verify it works:

```powershell
# Test DNS resolution
ping kafka
```

You should see:
```
Pinging kafka [127.0.0.1] with 32 bytes of data:
Reply from 127.0.0.1: bytes=32 time<1ms TTL=128
```

## After Adding to Hosts File

1. **Restart emergency-service in STS**
2. **Run the test:**
   ```powershell
   .\api-gateway\test-gateway-jwt.ps1
   ```

All 12 tests should pass!

## Why This Works

- Docker containers use internal DNS that resolves `kafka` to the Kafka container
- Services running in STS use Windows DNS, which doesn't know about `kafka`
- Adding `kafka` to the hosts file makes Windows resolve it to `127.0.0.1`
- Now the same configuration (`kafka:9092`) works in both Docker and STS

## Alternative: Use Profiles

If you don't want to modify the hosts file, you can use Spring profiles:

**application.yml** (default - for Docker):
```yaml
spring:
  kafka:
    bootstrap-servers: kafka:9092
```

**application-local.yml** (for STS):
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
```

Then run STS with: `--spring.profiles.active=local`

But the hosts file approach is cleaner because it requires no code changes.
