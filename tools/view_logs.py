#!/usr/bin/env python3
"""
Session Log Viewer for LyricPrompter
Pulls the database from the device and displays session logs in a nice format.

Usage:
    python3 view_logs.py              # Show all sessions
    python3 view_logs.py --latest     # Show latest session with events
    python3 view_logs.py --session ID # Show specific session with events
    python3 view_logs.py --watch      # Watch for new sessions (pull every 10s)
"""

import sqlite3
import subprocess
import os
import sys
import argparse
from datetime import datetime
from pathlib import Path

# Paths
ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
DEVICE_SERIAL = "54210DLAQ0028Y"
PACKAGE = "com.lyricprompter"
DB_NAME = "lyricprompter.db"
LOCAL_DB = "/tmp/lyricprompter_logs.db"

def pull_database():
    """Pull the database from the device."""
    # Use run-as to copy the database to a readable location, then pull it
    cmd_copy = f'{ADB} -s {DEVICE_SERIAL} shell "run-as {PACKAGE} cat databases/{DB_NAME}" > {LOCAL_DB}'
    result = subprocess.run(cmd_copy, shell=True, capture_output=True)

    if result.returncode != 0 or not os.path.exists(LOCAL_DB) or os.path.getsize(LOCAL_DB) == 0:
        print(f"Error pulling database: {result.stderr.decode()}")
        print("\nTrying alternative method...")

        # Try backup method
        backup_file = "/tmp/lyricprompter_backup.ab"
        cmd_backup = f'{ADB} -s {DEVICE_SERIAL} backup -f {backup_file} -noapk {PACKAGE}'
        print("Note: You may need to confirm backup on the device...")
        subprocess.run(cmd_backup, shell=True)
        return False

    return True

def format_duration(ms):
    """Format milliseconds as human-readable duration."""
    if ms is None:
        return "In progress"
    seconds = ms // 1000
    minutes = seconds // 60
    secs = seconds % 60
    if minutes > 0:
        return f"{minutes}m {secs}s"
    return f"{secs}s"

def format_timestamp(ts):
    """Format Unix timestamp as readable date/time."""
    return datetime.fromtimestamp(ts / 1000).strftime("%Y-%m-%d %H:%M:%S")

def format_event_time(ts):
    """Format timestamp for events (just time with millis)."""
    return datetime.fromtimestamp(ts / 1000).strftime("%H:%M:%S.%f")[:-3]

def get_sessions(conn, limit=None):
    """Get all sessions from the database."""
    cursor = conn.cursor()
    query = """
        SELECT id, startTime, endTime, durationMs, songTitle, songArtist,
               triggerPercent, promptWordCount, useFullLine, usePhoneMic,
               audioMode, bluetoothConnected, countInEnabled, bpm,
               linesPrompted, totalLines, averageMatchScore, totalRecognitions
        FROM session_logs
        ORDER BY startTime DESC
    """
    if limit:
        query += f" LIMIT {limit}"
    cursor.execute(query)
    return cursor.fetchall()

def get_session_by_id(conn, session_id):
    """Get a specific session."""
    cursor = conn.cursor()
    cursor.execute("""
        SELECT id, startTime, endTime, durationMs, songTitle, songArtist,
               triggerPercent, promptWordCount, useFullLine, usePhoneMic,
               audioMode, bluetoothConnected, countInEnabled, bpm,
               linesPrompted, totalLines, averageMatchScore, totalRecognitions
        FROM session_logs
        WHERE id = ?
    """, (session_id,))
    return cursor.fetchone()

def get_events(conn, session_id):
    """Get all events for a session."""
    cursor = conn.cursor()
    cursor.execute("""
        SELECT timestamp, eventType, recognizedText, recognizedWords,
               lineIndex, lineText, matchScore, threshold, thresholdMet,
               promptText, promptWordCount
        FROM log_events
        WHERE sessionId = ?
        ORDER BY timestamp ASC
    """, (session_id,))
    return cursor.fetchall()

def print_session_summary(session):
    """Print a summary of a session."""
    (session_id, start_time, end_time, duration_ms, song_title, song_artist,
     trigger_pct, prompt_words, use_full_line, use_phone_mic, audio_mode,
     bt_connected, count_in, bpm, lines_prompted, total_lines, avg_score,
     total_recognitions) = session

    print(f"\n{'='*70}")
    print(f"  {song_title} - {song_artist}")
    print(f"{'='*70}")
    print(f"  Session ID: {session_id[:8]}...")
    print(f"  Date/Time:  {format_timestamp(start_time)}")
    print(f"  Duration:   {format_duration(duration_ms)}")
    print(f"")
    print(f"  Settings:")
    print(f"    Trigger %:     {trigger_pct}%")
    print(f"    Prompt Words:  {'Full Line' if use_full_line else prompt_words}")
    print(f"    Audio Mode:    {audio_mode}")
    print(f"    Use Phone Mic: {'Yes' if use_phone_mic else 'No'}")
    print(f"    Bluetooth:     {'Connected' if bt_connected else 'Not Connected'}")
    print(f"    Count-In:      {'Yes' if count_in else 'No'}")
    if bpm:
        print(f"    BPM:           {bpm}")
    print(f"")
    print(f"  Results:")
    print(f"    Lines Prompted:    {lines_prompted} / {total_lines}")
    print(f"    Avg Match Score:   {f'{avg_score*100:.0f}%' if avg_score else 'N/A'}")
    print(f"    Total Recognitions: {total_recognitions}")

def print_events(events):
    """Print events for a session."""
    if not events:
        print("\n  No events recorded.")
        return

    print(f"\n  Event Log ({len(events)} events):")
    print(f"  {'-'*66}")

    # Color codes for terminal
    GREEN = '\033[92m'
    BLUE = '\033[94m'
    YELLOW = '\033[93m'
    PURPLE = '\033[95m'
    RESET = '\033[0m'

    for event in events:
        (timestamp, event_type, recognized_text, recognized_words,
         line_index, line_text, match_score, threshold, threshold_met,
         prompt_text, prompt_word_count) = event

        time_str = format_event_time(timestamp)

        # Color based on event type
        if event_type == "PROMPT_FIRED":
            color = GREEN
        elif event_type == "THRESHOLD_MET":
            color = BLUE
        elif event_type == "THRESHOLD_NOT_MET":
            color = YELLOW
        elif event_type in ("SESSION_START", "SESSION_END"):
            color = PURPLE
        else:
            color = RESET

        print(f"  {time_str}  {color}{event_type:<20}{RESET}", end="")

        if line_index is not None:
            print(f"  L{line_index:<3}", end="")
        else:
            print(f"       ", end="")

        if match_score is not None:
            score_str = f"{match_score*100:.0f}%"
            if threshold_met:
                print(f"  {GREEN}{score_str:>4}{RESET}", end="")
            else:
                print(f"  {YELLOW}{score_str:>4}{RESET}", end="")
        else:
            print(f"       ", end="")

        print()

        if recognized_text:
            print(f"                              \"{recognized_text[:50]}{'...' if len(recognized_text) > 50 else ''}\"")

        if prompt_text:
            print(f"                              {GREEN}Prompt: \"{prompt_text}\"{RESET}")

def list_sessions(conn):
    """List all sessions."""
    sessions = get_sessions(conn)

    if not sessions:
        print("\nNo session logs found. Perform a song to start logging!")
        return

    print(f"\n{'='*70}")
    print(f"  Session Logs ({len(sessions)} sessions)")
    print(f"{'='*70}")

    for session in sessions:
        (session_id, start_time, end_time, duration_ms, song_title, song_artist,
         trigger_pct, prompt_words, use_full_line, use_phone_mic, audio_mode,
         bt_connected, count_in, bpm, lines_prompted, total_lines, avg_score,
         total_recognitions) = session

        date_str = datetime.fromtimestamp(start_time / 1000).strftime("%m/%d %H:%M")
        duration_str = format_duration(duration_ms)
        audio = "Phone" if use_phone_mic else ("BT" if bt_connected else "Spkr")
        prompt_str = "Full" if use_full_line else str(prompt_words)
        score_str = f"{avg_score*100:.0f}%" if avg_score else "-"

        print(f"\n  [{session_id[:8]}] {song_title[:25]:<25} {song_artist[:15]:<15}")
        print(f"             {date_str}  {duration_str:>8}  {audio:>5}  T:{trigger_pct}% P:{prompt_str}")
        print(f"             Prompted: {lines_prompted}/{total_lines}  Avg: {score_str}  Recogs: {total_recognitions}")

def show_latest(conn):
    """Show the latest session with events."""
    sessions = get_sessions(conn, limit=1)
    if not sessions:
        print("\nNo session logs found.")
        return

    session = sessions[0]
    print_session_summary(session)

    events = get_events(conn, session[0])
    print_events(events)

def show_session(conn, session_id):
    """Show a specific session with events."""
    # Try to match partial ID
    cursor = conn.cursor()
    cursor.execute("SELECT id FROM session_logs WHERE id LIKE ?", (f"{session_id}%",))
    matches = cursor.fetchall()

    if not matches:
        print(f"\nNo session found matching '{session_id}'")
        return

    if len(matches) > 1:
        print(f"\nMultiple sessions match '{session_id}':")
        for m in matches:
            print(f"  {m[0]}")
        return

    full_id = matches[0][0]
    session = get_session_by_id(conn, full_id)
    print_session_summary(session)

    events = get_events(conn, full_id)
    print_events(events)

def watch_mode(conn):
    """Watch for new sessions."""
    import time

    print("\nWatching for new sessions (Ctrl+C to stop)...")
    last_count = 0

    try:
        while True:
            if pull_database():
                conn = sqlite3.connect(LOCAL_DB)
                sessions = get_sessions(conn, limit=1)
                current_count = len(get_sessions(conn))

                if current_count > last_count and last_count > 0:
                    print(f"\n{'*'*70}")
                    print("  NEW SESSION DETECTED!")
                    show_latest(conn)
                    print(f"{'*'*70}")

                last_count = current_count
                conn.close()

            time.sleep(10)
    except KeyboardInterrupt:
        print("\nStopped watching.")

def main():
    parser = argparse.ArgumentParser(description="View LyricPrompter session logs")
    parser.add_argument("--latest", action="store_true", help="Show latest session with events")
    parser.add_argument("--session", type=str, help="Show specific session (use first 8 chars of ID)")
    parser.add_argument("--watch", action="store_true", help="Watch for new sessions")
    args = parser.parse_args()

    print("Pulling database from device...")
    if not pull_database():
        print("Failed to pull database. Make sure the device is connected and app is installed.")
        sys.exit(1)

    print("Database pulled successfully.")

    conn = sqlite3.connect(LOCAL_DB)

    try:
        if args.watch:
            watch_mode(conn)
        elif args.latest:
            show_latest(conn)
        elif args.session:
            show_session(conn, args.session)
        else:
            list_sessions(conn)
    finally:
        conn.close()

if __name__ == "__main__":
    main()
