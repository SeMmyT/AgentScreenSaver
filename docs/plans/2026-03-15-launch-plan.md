# Agent ScreenSaver — Play Store Launch Plan

**Goal:** Ship Agent ScreenSaver to Google Play with $1/mo, $5.75/yr, $9.85 lifetime pricing.

**Timeline:** ~3 weeks (dominated by 14-day testing gate)

---

## Phase 1: Developer Account (Days 1-3)

### Task 1: Register Google Play Developer Account
- Go to https://play.google.com/console/signup
- Pay $25 one-time fee
- Submit ID verification (driver's license/passport)
- Complete device verification (install Play Console app on Pixel, scan QR code)
- Wait for identity verification approval (~1-3 days)

---

## Phase 2: App Polish (Days 1-5, parallel with Phase 1)

### Task 2: Privacy Policy
- Host on GitHub Pages at `https://semmyt.github.io/AgentScreenSaver/privacy`
- Content: app connects to user-configured local servers only, no data collected/transmitted to third parties, no analytics, no tracking
- Must be a publicly accessible URL (not PDF, not geo-fenced)

### Task 3: Onboarding / Empty State
- When no server is connected, show a helpful setup guide instead of blank screen
- This prevents Play Store reviewer rejection ("broken functionality")
- Show: "Connect to your bridge server" with step-by-step instructions
- Include a "Demo Mode" button that shows fake animated status for reviewers

### Task 4: App Icon
- Design app icon (the Clawd pixel crab on dark background)
- Sizes: 512x512 (Play Store) + adaptive icon for Android (foreground + background layers)
- Files: `res/mipmap-*/ic_launcher.png`, `res/drawable/ic_launcher_foreground.xml`

### Task 5: Store Listing Assets
- 5-6 phone screenshots (portrait + landscape showing terminal panes)
- Feature graphic: 1024x500 (Clawd + "Agent ScreenSaver" + terminal pane preview)
- Short description (80 chars): "Real-time AI agent status on your charging stand"
- Full description (4000 chars): what it does, how to set up, features, pricing

### Task 6: Build Signed AAB
- Generate upload keystore: `keytool -genkey -v -keystore upload.jks -keyalg RSA -keysize 2048 -validity 10000`
- Build: `./gradlew bundleRelease`
- Store keystore securely (NOT in git)

---

## Phase 3: Play Console Setup (Days 3-5)

### Task 7: Create App in Play Console
- App name: "Agent ScreenSaver"
- Category: Tools
- Content rating: complete IARC questionnaire → "Everyone"
- Target audience: 18+ (developer tool)

### Task 8: Data Safety Form
- Declare: "No data collected or shared"
- Check: app uses INTERNET permission for local network only
- Link privacy policy URL

### Task 9: Create Subscription Products
- In Play Console → Monetize → Products → Subscriptions:
  - `monthly_pro`: $1.00/month auto-renewing
  - `annual_pro`: $5.75/year auto-renewing
- In Play Console → Monetize → Products → In-app products:
  - `lifetime_pro`: $9.85 one-time purchase
- Activate all products

### Task 10: Upload AAB to Internal Testing Track
- Upload signed AAB
- Set up internal testing (for your own device first)
- Verify billing flow works end-to-end on real device

---

## Phase 4: Closed Testing (Days 5-19, 14-day mandatory gate)

### Task 11: Set Up Closed Testing Track
- Create closed testing track in Play Console
- Generate opt-in link
- Recruit 12+ testers (sources below)

### Task 12: Recruit 12 Testers
**Sources:**
- r/ClaudeAI subreddit (post asking for beta testers)
- Claude Code GitHub Discussions
- Hacker News (Show HN when app is more polished)
- Friends/colleagues who use Claude Code
- X/Twitter developer community

**Requirements:**
- Each tester must opt in via the testing link
- Must remain opted in for 14 consecutive days
- At least 12 testers at all times

### Task 13: Iterate Based on Tester Feedback
- Fix bugs reported during closed testing
- Polish UI based on real-device usage
- Verify screensaver (DreamService) works across different phone models
- Test billing flow with test purchases

---

## Phase 5: Production Launch (Days 19-25)

### Task 14: Apply for Production Access
- After 14-day testing gate passes, apply in Play Console
- Google reviews (~3-7 days)

### Task 15: Prepare Launch Marketing
- GitHub README with screenshots, setup instructions, pricing
- r/ClaudeAI launch post
- Show HN post
- X/Twitter announcement

### Task 16: Production Release
- Promote from closed testing to production
- Set rollout percentage (start at 20%, ramp to 100%)
- Monitor crash reports and reviews

---

## Remaining Code Tasks (Before Upload)

| Task | Priority | Effort |
|------|----------|--------|
| Onboarding/empty state screen | P0 — blocks review | 1 hr |
| Demo mode for reviewers | P0 — blocks review | 1 hr |
| App icon (Clawd) | P0 — required for listing | 30 min |
| Privacy policy page | P0 — required for listing | 30 min |
| Test billing on real device | P0 — must work | 1 hr |
| Back navigation from dashboard/paywall | P1 | 30 min |
| Session expiry (remove stale sessions after 30 min) | P1 | 30 min |
| Bridge server: add `--advertise-ip` flag | P2 | 15 min |
| Bridge server: systemd service file | P2 | 15 min |
| Sound pack: download/bundle game sounds | P2 | 1 hr |
| README.md for GitHub | P2 | 30 min |
