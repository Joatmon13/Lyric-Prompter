# Lyric Prompter

A native Android app that helps guitarists remember lyrics during performance. It uses offline speech recognition (Vosk) to track what you're singing and speaks prompts for upcoming lines through your Bluetooth earpiece.

## How It Works

1. **Add a song** - Search for lyrics online or enter manually
2. **Set up** - Configure BPM for count-in, trigger percentage, etc.
3. **Perform** - The app listens to you sing and prompts the next line when you're ~70% through the current one

The app follows YOUR tempo - no beat matching or karaoke-style timing. Just voice recognition that tracks your position and prompts at the right moment.

## Features

- Offline speech recognition (Vosk) - no internet needed during performance
- Automatic lyrics search via LRCLIB
- BPM lookup for count-in timing
- Bluetooth earpiece support for discrete prompts
- Per-song settings (trigger %, prompt words, count-in beats)
- Setlist management

## Tech Stack

- Kotlin + Jetpack Compose
- MVVM + Clean Architecture
- Hilt for dependency injection
- Room for local database
- Vosk for offline speech recognition

## Credits

- Lyrics data provided by [LRCLIB](https://lrclib.net)
- BPM data provided by [GetSongBPM](https://getsongbpm.com)

## License

Apache License 2.0 - See LICENSE file for details.
