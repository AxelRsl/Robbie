import React, { useRef, useCallback } from 'react';
import { View, StyleSheet, NativeModules, Text, TouchableOpacity } from 'react-native';
import Video from 'react-native-video';

const { TourMediaModule } = NativeModules;

interface TourVideoScreenProps {
  videoPath: string;
  onFinished: () => void;
}

/**
 * Fullscreen video player for tour guide stops.
 * Uses react-native-video (now properly linked via Gradle) to play
 * local video files from the robot's filesystem.
 * Notifies Java (TourMediaPlayer → TourExecutor) when the video finishes.
 */
export const TourVideoScreen: React.FC<TourVideoScreenProps> = ({ videoPath, onFinished }) => {
  const videoRef = useRef<any>(null);

  const handleEnd = useCallback(() => {
    console.log('[TourVideo] Video ended:', videoPath);
    TourMediaModule?.onVideoFinished?.().catch((e: any) => {
      console.warn('[TourVideo] Error notifying Java:', e);
    });
    onFinished();
  }, [videoPath, onFinished]);

  const handleError = useCallback((error: any) => {
    console.error('[TourVideo] Video error:', JSON.stringify(error));
    TourMediaModule?.onVideoError?.(String(error?.error?.errorString || 'unknown'))?.catch(() => {});
    // Wait 2s then dismiss so user can see error state briefly
    setTimeout(() => onFinished(), 2000);
  }, [onFinished]);

  const handleSkip = useCallback(() => {
    console.log('[TourVideo] Video skipped by user');
    TourMediaModule?.onVideoFinished?.().catch(() => {});
    onFinished();
  }, [onFinished]);

  const handleLoad = useCallback((data: any) => {
    console.log('[TourVideo] Video loaded, duration:', data?.duration, 'seconds');
  }, []);

  return (
    <View style={styles.container}>
      <Video
        ref={videoRef}
        source={{ uri: 'file://' + videoPath }}
        style={styles.video}
        resizeMode="contain"
        onEnd={handleEnd}
        onError={handleError}
        onLoad={handleLoad}
        controls={false}
        fullscreen={false}
        repeat={false}
        paused={false}
      />
      {/* Skip button — bottom-right, semi-transparent */}
      <TouchableOpacity style={styles.skipButton} onPress={handleSkip} activeOpacity={0.7}>
        <Text style={styles.skipText}>Omitir ▶▶</Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: '#000',
    zIndex: 9999,
    elevation: 9999,
  },
  video: {
    flex: 1,
    width: '100%',
    height: '100%',
  },
  skipButton: {
    position: 'absolute',
    bottom: 30,
    right: 30,
    backgroundColor: 'rgba(255,255,255,0.15)',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.3)',
  },
  skipText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
});
