#!/bin/bash
# Live view of LyricPrompter logs
# Usage: ./lp-log-live.sh [verbosity]
#   verbosity: quick | normal | deep | all (default: normal)

VERBOSITY=${1:-normal}

case $VERBOSITY in
    quick)
        echo "Quick view (prompts and session only)..."
        adb logcat -v time -s LP.Session:I LP.Tracker:I
        ;;
    normal)
        echo "Normal debugging view..."
        adb logcat -v time -s LP.Session:I LP.Audio:I LP.Vosk:D LP.Matcher:D LP.Tracker:I LP.Prompt:I
        ;;
    deep)
        echo "Deep matching analysis..."
        adb logcat -v time -s LP.Matcher:V LP.Tracker:D LP.Vosk:D
        ;;
    all)
        echo "All LyricPrompter logs..."
        adb logcat -v time -s LP.Session:V LP.Audio:V LP.Vosk:V LP.Matcher:V LP.Tracker:V LP.Prompt:V
        ;;
    *)
        echo "Usage: $0 [quick|normal|deep|all]"
        exit 1
        ;;
esac
