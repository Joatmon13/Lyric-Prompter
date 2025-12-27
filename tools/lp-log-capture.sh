#!/bin/bash
# Capture a LyricPrompter test session to file
# Usage: ./lp-log-capture.sh

LOG_DIR=~/Documents/LyricPrompter/logs
mkdir -p "$LOG_DIR"

SESSION_ID=$(date +%Y%m%d_%H%M%S)
SESSION_FILE="$LOG_DIR/session_$SESSION_ID.log"

# Prompt for notes
echo "================================================"
echo "LyricPrompter Log Capture"
echo "================================================"
echo ""
echo "Test notes (e.g., '25% trigger, phone mic, Lyin Eyes'):"
read NOTES

# Clear existing logs
adb logcat -c

# Write header
cat > "$SESSION_FILE" << EOF
================================================================================
LYRIC PROMPTER TEST SESSION
================================================================================
Session ID: $SESSION_ID
Date: $(date)
Notes: $NOTES
================================================================================

EOF

echo ""
echo "Logging to: $SESSION_FILE"
echo "Press Ctrl+C to stop..."
echo ""

# Capture logs
adb logcat -v time -s LP.Session:I LP.Audio:I LP.Vosk:D LP.Matcher:D LP.Tracker:I LP.Prompt:I | tee -a "$SESSION_FILE"
