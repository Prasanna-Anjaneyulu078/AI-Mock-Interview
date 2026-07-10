class VoiceQueueService {
  constructor() {
    this.queue = [];
    this.isPlaying = false;
    this.currentAudio = null;
    this.isMuted = false;
    this.volume = 1.0;
    this.playbackRate = 1.0;
    
    // Callbacks for UI updates
    this.onPlay = null;
    this.onStop = null;
  }

  /**
   * Add audio to the queue and try playing it
   * @param {string} url Data URL or blob URL of the audio
   * @param {Function} onComplete Callback when this specific audio finishes
   */
  play(url, onComplete = null) {
    if (!url) {
      if (onComplete) onComplete();
      return;
    }
    
    this.queue.push({ url, onComplete });
    this._processQueue();
  }

  /**
   * Force play audio immediately, interrupting current playback and clearing queue
   */
  playNow(url, onComplete = null) {
    if (!url) {
      if (onComplete) onComplete();
      return;
    }
    
    this.stopAll();
    this.queue = [{ url, onComplete }];
    this._processQueue();
  }

  _processQueue() {
    if (this.isPlaying || this.queue.length === 0 || this.isMuted) {
      return;
    }

    const next = this.queue.shift();
    this.isPlaying = true;
    
    if (this.onPlay) this.onPlay();

    this.currentAudio = new Audio(next.url);
    this.currentAudio.volume = this.volume;
    this.currentAudio.playbackRate = this.playbackRate;

    this.currentAudio.onended = () => {
      this.isPlaying = false;
      this.currentAudio = null;
      if (this.onStop) this.onStop();
      if (next.onComplete) next.onComplete();
      this._processQueue();
    };

    this.currentAudio.onerror = (e) => {
      console.error("Audio playback error:", e);
      this.isPlaying = false;
      this.currentAudio = null;
      if (this.onStop) this.onStop();
      if (next.onComplete) next.onComplete();
      this._processQueue();
    };

    this.currentAudio.play().catch(e => {
      if (e.name === 'AbortError') {
        // Play was intentionally interrupted by a pause/stop call, safely ignore
        return;
      }
      console.error("Failed to play audio:", e);
      this.isPlaying = false;
      this.currentAudio = null;
      if (this.onStop) this.onStop();
      if (next.onComplete) next.onComplete();
      this._processQueue();
    });
  }

  stopAll() {
    this.queue = [];
    if (this.currentAudio) {
      this.currentAudio.pause();
      this.currentAudio = null;
    }
    this.isPlaying = false;
    if (this.onStop) this.onStop();
  }

  pause() {
    if (this.currentAudio && this.isPlaying) {
      this.currentAudio.pause();
      this.isPlaying = false;
      if (this.onStop) this.onStop();
    }
  }

  resume() {
    if (this.currentAudio && !this.isPlaying && !this.isMuted) {
      this.currentAudio.play();
      this.isPlaying = true;
      if (this.onPlay) this.onPlay();
    }
  }

  setMute(mute) {
    this.isMuted = mute;
    if (mute) {
      this.stopAll();
    }
  }

  setVolume(vol) {
    this.volume = vol;
    if (this.currentAudio) {
      this.currentAudio.volume = vol;
    }
  }

  setSpeed(speed) {
    this.playbackRate = speed;
    if (this.currentAudio) {
      this.currentAudio.playbackRate = speed;
    }
  }
}

export default new VoiceQueueService();
