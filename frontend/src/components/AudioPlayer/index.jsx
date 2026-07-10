import { useEffect, useState } from 'react';
import { BsPlayFill, BsPauseFill, BsArrowCounterclockwise, BsVolumeUpFill, BsVolumeMuteFill } from 'react-icons/bs';
import VoiceQueueService from '../../services/VoiceQueueService';
import './index.css';

function AudioPlayer({ audioSrc, autoPlay, onEnded, audioText }) {
  const [isPlaying, setIsPlaying] = useState(false);
  const [isMuted, setIsMuted] = useState(false);

  useEffect(() => {
    // Bind UI state to the queue service
    VoiceQueueService.onPlay = () => setIsPlaying(true);
    VoiceQueueService.onStop = () => setIsPlaying(false);

    if (audioSrc && autoPlay) {
      let finalSrc = audioSrc;
      if (audioSrc.startsWith('JVBERi0x') || (!audioSrc.startsWith('http') && !audioSrc.startsWith('data:'))) {
        try {
           const binaryString = atob(audioSrc);
           const bytes = new Uint8Array(binaryString.length);
           for (let i = 0; i < binaryString.length; i++) {
             bytes[i] = binaryString.charCodeAt(i);
           }
           finalSrc = URL.createObjectURL(new Blob([bytes], { type: 'audio/mp3' }));
        } catch(e) {}
      }
      VoiceQueueService.playNow(finalSrc, onEnded);
    }
    
    return () => {
      // Cleanup if unmounted while playing this specific instance
      VoiceQueueService.onPlay = null;
      VoiceQueueService.onStop = null;
    };
  }, [audioSrc, autoPlay]);

  const togglePlay = () => {
    if (isPlaying) {
      VoiceQueueService.pause();
    } else {
      VoiceQueueService.resume();
    }
  };

  const toggleMute = () => {
    const nextMute = !isMuted;
    setIsMuted(nextMute);
    VoiceQueueService.setMute(nextMute);
  };

  const replay = () => {
    if (audioSrc) {
      let finalSrc = audioSrc;
      if (!audioSrc.startsWith('http') && !audioSrc.startsWith('data:')) {
        try {
           const binaryString = atob(audioSrc);
           const bytes = new Uint8Array(binaryString.length);
           for (let i = 0; i < binaryString.length; i++) bytes[i] = binaryString.charCodeAt(i);
           finalSrc = URL.createObjectURL(new Blob([bytes], { type: 'audio/mp3' }));
        } catch(e) {}
      }
      VoiceQueueService.playNow(finalSrc, onEnded);
    }
  };

  return (
    <div className="audio-player-controls">
      <button className="audio-ctrl-btn" onClick={togglePlay} title={isPlaying ? "Pause" : "Play"}>
        {isPlaying ? <BsPauseFill /> : <BsPlayFill />}
      </button>
      <button className="audio-ctrl-btn" onClick={replay} title="Replay">
        <BsArrowCounterclockwise />
      </button>
      <button className="audio-ctrl-btn" onClick={toggleMute} title={isMuted ? "Unmute" : "Mute"}>
        {isMuted ? <BsVolumeMuteFill /> : <BsVolumeUpFill />}
      </button>
      <input 
        type="range" 
        min="0" max="1" step="0.1" 
        defaultValue="1" 
        className="audio-vol-slider"
        onChange={(e) => VoiceQueueService.setVolume(parseFloat(e.target.value))}
      />
      <select 
        className="audio-speed-select" 
        defaultValue="1" 
        onChange={(e) => VoiceQueueService.setSpeed(parseFloat(e.target.value))}
      >
        <option value="0.75">0.75x</option>
        <option value="1">1x</option>
        <option value="1.25">1.25x</option>
        <option value="1.5">1.5x</option>
      </select>
    </div>
  );
}

export default AudioPlayer;
