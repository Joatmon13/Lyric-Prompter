#!/bin/bash
# Quick analysis of a LyricPrompter session log
# Usage: ./lp-log-analyze.sh <logfile>

LOG_FILE=$1

if [ -z "$LOG_FILE" ]; then
    echo "Usage: $0 <logfile>"
    echo ""
    echo "Available logs:"
    ls -la ~/Documents/LyricPrompter/logs/*.log 2>/dev/null || echo "No logs found in ~/Documents/LyricPrompter/logs/"
    exit 1
fi

if [ ! -f "$LOG_FILE" ]; then
    echo "Error: File not found: $LOG_FILE"
    exit 1
fi

echo "================================================"
echo "LyricPrompter Log Analysis"
echo "File: $LOG_FILE"
echo "================================================"
echo ""

echo "=== SESSION INFO ==="
grep -E "\[SESSION_START\]|\[SESSION_END\]|\[SONG_LOADED\]" "$LOG_FILE"
echo ""

echo "=== AUDIO CONFIGURATION ==="
grep "\[AUDIO_CONFIG\]" "$LOG_FILE"
echo ""

echo "=== PROMPTS FIRED ==="
PROMPT_COUNT=$(grep "\[PROMPT_FIRED\]" "$LOG_FILE" | wc -l | tr -d ' ')
echo "Total prompts: $PROMPT_COUNT"
grep "\[PROMPT_FIRED\]" "$LOG_FILE"
echo ""

echo "=== LINES SKIPPED ==="
grep "\[LINE_SKIP\]" "$LOG_FILE" || echo "(none)"
echo ""

echo "=== LOW MATCH SCORES (<30%) ==="
grep "\[MATCH_RESULT\]" "$LOG_FILE" | grep -E "score=[0-2][0-9]%" || echo "(none found)"
echo ""

echo "=== THRESHOLD NOT MET (near misses) ==="
grep "\[THRESHOLD_NOT_MET\]" "$LOG_FILE" | tail -10 || echo "(none)"
echo ""

echo "=== VOSK RECOGNITION SAMPLES (last 15) ==="
grep "\[VOSK_FINAL\]" "$LOG_FILE" | tail -15
echo ""

echo "=== TRIGGER_PROMPT EVENTS ==="
grep "\[TRIGGER_PROMPT\]" "$LOG_FILE"
echo ""

echo "================================================"
echo "Analysis complete"
echo "================================================"
