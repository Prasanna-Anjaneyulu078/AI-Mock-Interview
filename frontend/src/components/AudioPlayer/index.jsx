import { useEffect, useRef } from 'react';

/**
 * Plays interview audio. Accepts either a remote URL (Murf audioFile) or a
 * base64 data string (legacy/fallback).
 *
 * Resilience:
 *  - Guards against the classic play()/pause() race so unmounting an audio
 *    element never logs a spurious "interrupted by a call to pause()" error.
 *  - If the remote audio fails to load (e.g. expired Murf URL, network/CORS),
 *    it falls back to the browser's built-in Web Speech API (when text is
 *    supplied) so the interviewer still speaks. If that is unavailable, it just
 *    advances gracefully instead of crashing the flow.
 */
function AudioPlayer({ audioSrc, autoPlay, onEnded, audioText }) {
  const endedRef = useRef(false);
  const onEndedRef = useRef(onEnded);
  onEndedRef.current = onEnded;

  useEffect(() => {
    if (!audioSrc) return;
    endedRef.current = false;

    let objectUrl = null;
    let cancelled = false;
    const audio = new Audio();

    const finish = () => {
      if (endedRef.current) return; // fire onEnded at most once
      endedRef.current = true;
      if (onEndedRef.current) onEndedRef.current();
    };

    const useSpeechFallback = () => {
      if (audioText && typeof window !== 'undefined' && window.speechSynthesis) {
        try {
          const utterance = new SpeechSynthesisUtterance(audioText);
          utterance.onend = finish;
          utterance.onerror = finish;
          window.speechSynthesis.cancel();
          window.speechSynthesis.speak(utterance);
          return;
        } catch (_) {
          /* fall through to plain advance */
        }
      }
      finish();
    };

    audio.onended = finish;
    audio.onerror = () => {
      console.warn('Audio unavailable, using speech fallback:', audioSrc);
      useSpeechFallback();
    };

    const isBase64 =
      audioSrc.startsWith('data:') ||
      (!audioSrc.startsWith('http://') && !audioSrc.startsWith('https://'));

    if (isBase64) {
      try {
        const binaryString = atob(audioSrc);
        const bytes = new Uint8Array(binaryString.length);
        for (let i = 0; i < binaryString.length; i++) {
          bytes[i] = binaryString.charCodeAt(i);
        }
        objectUrl = URL.createObjectURL(new Blob([bytes], { type: 'audio/mp3' }));
        audio.src = objectUrl;
      } catch (e) {
        console.warn('Failed to decode audio:', e);
        useSpeechFallback();
        return;
      }
    } else {
      audio.src = audioSrc;
    }

    if (autoPlay) {
      audio.play().catch((err) => {
        if (cancelled) return; // interrupted by unmount -> ignore, not an error
        console.warn('Audio autoplay unavailable:', err.message);
        useSpeechFallback();
      });
    }

    return () => {
      cancelled = true;
      try {
        audio.onended = null;
        audio.onerror = null;
        audio.pause();
        if (objectUrl) URL.revokeObjectURL(objectUrl);
        audio.src = '';
      } catch (_) {
        /* ignore cleanup errors */
      }
      if (typeof window !== 'undefined' && window.speechSynthesis) {
        window.speechSynthesis.cancel();
      }
    };
  }, [audioSrc]);

  return null;
}

export default AudioPlayer;
