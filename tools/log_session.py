#!/usr/bin/env python3
"""
LyricPrompter Test Session Logger

Captures logcat output during a performance session and stores metrics
in a SQLite database for analysis across different builds/settings.

Usage:
    python log_session.py start "Session description"
    python log_session.py list
    python log_session.py analyze <session_id>
    python log_session.py compare <session_id1> <session_id2>
"""

import sqlite3
import subprocess
import sys
import os
import re
import json
from datetime import datetime
from pathlib import Path

DB_PATH = Path(__file__).parent / "sessions.db"
ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
DEVICE_ID = "54210DLAQ0028Y"

def init_db():
    """Initialize the database schema."""
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()

    c.execute('''
        CREATE TABLE IF NOT EXISTS sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp TEXT NOT NULL,
            description TEXT,
            song_title TEXT,
            trigger_percent INTEGER,
            prompt_word_count INTEGER,
            audio_mode TEXT,
            duration_seconds REAL,
            total_lines INTEGER,
            lines_prompted INTEGER,
            lines_skipped INTEGER,
            recognition_accuracy REAL,
            notes TEXT
        )
    ''')

    c.execute('''
        CREATE TABLE IF NOT EXISTS recognition_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id INTEGER,
            timestamp TEXT,
            event_type TEXT,
            line_index INTEGER,
            recognized_words TEXT,
            expected_words TEXT,
            match_score REAL,
            triggered_prompt INTEGER,
            FOREIGN KEY (session_id) REFERENCES sessions(id)
        )
    ''')

    conn.commit()
    return conn

def capture_session(description):
    """Start capturing a session and store results."""
    conn = init_db()
    c = conn.cursor()

    print(f"Starting session: {description}")
    print("Clearing logcat and waiting for performance...")
    print("Press Ctrl+C when done to save the session.\n")

    # Clear logcat
    subprocess.run([ADB, "-s", DEVICE_ID, "logcat", "-c"], capture_output=True)

    start_time = datetime.now()
    logs = []

    try:
        # Stream logcat
        process = subprocess.Popen(
            [ADB, "-s", DEVICE_ID, "logcat",
             "PerformViewModel:V", "PositionTracker:V", "VoskEngine:V",
             "PromptSpeaker:V", "CountInPlayer:V", "*:S"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )

        for line in process.stdout:
            logs.append(line.strip())
            # Show key events in real-time
            if "PROMPT" in line:
                print(f"  PROMPT: {line.split('PROMPT')[-1].strip()}")
            elif "Recognized words:" in line:
                words = re.search(r'\[(.*?)\]', line)
                if words:
                    print(f"  Heard: [{words.group(1)}]")

    except KeyboardInterrupt:
        process.terminate()

    end_time = datetime.now()
    duration = (end_time - start_time).total_seconds()

    # Parse logs
    metrics = parse_logs(logs)

    # Insert session
    c.execute('''
        INSERT INTO sessions
        (timestamp, description, song_title, trigger_percent, duration_seconds,
         total_lines, lines_prompted, lines_skipped, recognition_accuracy)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    ''', (
        start_time.isoformat(),
        description,
        metrics.get('song_title'),
        metrics.get('trigger_percent'),
        duration,
        metrics.get('total_lines', 0),
        metrics.get('lines_prompted', 0),
        metrics.get('lines_skipped', 0),
        metrics.get('accuracy', 0)
    ))

    session_id = c.lastrowid

    # Insert recognition events
    for event in metrics.get('events', []):
        c.execute('''
            INSERT INTO recognition_events
            (session_id, timestamp, event_type, line_index, recognized_words,
             match_score, triggered_prompt)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        ''', (
            session_id,
            event.get('timestamp'),
            event.get('type'),
            event.get('line_index'),
            json.dumps(event.get('words', [])),
            event.get('score'),
            event.get('triggered', 0)
        ))

    conn.commit()
    conn.close()

    print(f"\n{'='*50}")
    print(f"Session {session_id} saved!")
    print(f"Duration: {duration:.1f}s")
    print(f"Song: {metrics.get('song_title', 'Unknown')}")
    print(f"Trigger: {metrics.get('trigger_percent', '?')}%")
    print(f"Lines prompted: {metrics.get('lines_prompted', 0)}/{metrics.get('total_lines', '?')}")
    print(f"Lines skipped: {metrics.get('lines_skipped', 0)}")
    print(f"{'='*50}")

def parse_logs(logs):
    """Parse logcat output into metrics."""
    metrics = {
        'events': [],
        'lines_prompted': 0,
        'lines_skipped': 0,
        'total_lines': 0
    }

    for line in logs:
        # Song title
        if "Song loaded:" in line:
            match = re.search(r'Song loaded: (.*?),', line)
            if match:
                metrics['song_title'] = match.group(1)

        # Trigger percent
        if "trigger=" in line:
            match = re.search(r'trigger=(\d+)%', line)
            if match:
                metrics['trigger_percent'] = int(match.group(1))

        # Total lines
        if "totalLines" in line or "vocabulary size:" in line:
            match = re.search(r'(\d+) words', line)
            # We'll get total lines from prompts

        # Recognition events
        if "Recognized words:" in line:
            match = re.search(r'\[(.*?)\]', line)
            ts_match = re.search(r'(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})', line)
            if match:
                metrics['events'].append({
                    'type': 'recognition',
                    'timestamp': ts_match.group(1) if ts_match else None,
                    'words': match.group(1).split(', ') if match.group(1) else []
                })

        # Prompts
        if "PROMPT line" in line:
            match = re.search(r'PROMPT line (\d+):', line)
            if match:
                line_idx = int(match.group(1))
                metrics['lines_prompted'] += 1
                metrics['total_lines'] = max(metrics['total_lines'], line_idx + 1)
                if metrics['events']:
                    metrics['events'][-1]['triggered'] = 1
                    metrics['events'][-1]['line_index'] = line_idx

        # Skips
        if "Detected skip:" in line:
            metrics['lines_skipped'] += 1

        # Match scores
        if "Match: line" in line:
            match = re.search(r'line (\d+) \((\d+)%\)', line)
            if match and metrics['events']:
                metrics['events'][-1]['line_index'] = int(match.group(1))
                metrics['events'][-1]['score'] = int(match.group(2)) / 100.0

    return metrics

def list_sessions():
    """List all recorded sessions."""
    conn = init_db()
    c = conn.cursor()

    c.execute('''
        SELECT id, timestamp, description, song_title, trigger_percent,
               duration_seconds, lines_prompted, lines_skipped
        FROM sessions ORDER BY id DESC LIMIT 20
    ''')

    rows = c.fetchall()
    conn.close()

    if not rows:
        print("No sessions recorded yet.")
        return

    print(f"{'ID':>4} {'Date':>12} {'Song':<25} {'Trig%':>5} {'Dur':>6} {'Prompts':>8} {'Skips':>6}")
    print("-" * 75)

    for row in rows:
        id, ts, desc, song, trigger, dur, prompts, skips = row
        date = ts.split('T')[0] if ts else ''
        song = (song or desc or '')[:24]
        print(f"{id:>4} {date:>12} {song:<25} {trigger or '?':>5} {dur or 0:>5.0f}s {prompts or 0:>8} {skips or 0:>6}")

def analyze_session(session_id):
    """Show detailed analysis of a session."""
    conn = init_db()
    c = conn.cursor()

    c.execute('SELECT * FROM sessions WHERE id = ?', (session_id,))
    session = c.fetchone()

    if not session:
        print(f"Session {session_id} not found.")
        return

    c.execute('''
        SELECT event_type, line_index, recognized_words, match_score, triggered_prompt
        FROM recognition_events WHERE session_id = ? ORDER BY id
    ''', (session_id,))
    events = c.fetchall()
    conn.close()

    print(f"\nSession {session_id} Analysis")
    print("=" * 50)
    print(f"Description: {session[2]}")
    print(f"Song: {session[3]}")
    print(f"Trigger: {session[4]}%")
    print(f"Duration: {session[6]:.1f}s")
    print(f"Lines Prompted: {session[7]}/{session[8] + session[7] if session[8] else session[7]}")
    print(f"Lines Skipped: {session[8]}")

    if events:
        print(f"\nRecognition Events ({len(events)} total):")
        print("-" * 50)

        triggered = [e for e in events if e[4]]
        scores = [e[3] for e in events if e[3]]

        if scores:
            print(f"Avg match score: {sum(scores)/len(scores)*100:.1f}%")
            print(f"Max match score: {max(scores)*100:.1f}%")
            print(f"Triggers: {len(triggered)}")

def compare_sessions(id1, id2):
    """Compare two sessions."""
    conn = init_db()
    c = conn.cursor()

    c.execute('SELECT * FROM sessions WHERE id IN (?, ?)', (id1, id2))
    sessions = c.fetchall()
    conn.close()

    if len(sessions) != 2:
        print("Could not find both sessions.")
        return

    print(f"\nComparison: Session {id1} vs {id2}")
    print("=" * 50)

    labels = ['ID', 'Song', 'Trigger%', 'Duration', 'Prompted', 'Skipped']
    s1, s2 = sessions

    print(f"{'Metric':<15} {'Session '+str(id1):<20} {'Session '+str(id2):<20}")
    print("-" * 55)
    print(f"{'Song':<15} {str(s1[3] or '')[:19]:<20} {str(s2[3] or '')[:19]:<20}")
    print(f"{'Trigger%':<15} {s1[4] or '?':<20} {s2[4] or '?':<20}")
    print(f"{'Duration':<15} {f'{s1[6]:.0f}s' if s1[6] else '?':<20} {f'{s2[6]:.0f}s' if s2[6] else '?':<20}")
    print(f"{'Prompted':<15} {s1[7] or 0:<20} {s2[7] or 0:<20}")
    print(f"{'Skipped':<15} {s1[8] or 0:<20} {s2[8] or 0:<20}")

def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return

    command = sys.argv[1]

    if command == "start":
        desc = sys.argv[2] if len(sys.argv) > 2 else "Test session"
        capture_session(desc)
    elif command == "list":
        list_sessions()
    elif command == "analyze":
        if len(sys.argv) < 3:
            print("Usage: log_session.py analyze <session_id>")
            return
        analyze_session(int(sys.argv[2]))
    elif command == "compare":
        if len(sys.argv) < 4:
            print("Usage: log_session.py compare <id1> <id2>")
            return
        compare_sessions(int(sys.argv[2]), int(sys.argv[3]))
    else:
        print(f"Unknown command: {command}")
        print(__doc__)

if __name__ == "__main__":
    main()
