declare module 'react-native-video' {
  import * as React from 'react';
  import { StyleProp, ViewStyle } from 'react-native';

  export interface OnLoadData {
    duration: number;
    currentTime?: number;
  }

  export interface OnErrorData {
    error?: {
      errorString?: string;
    };
  }

  export interface VideoSource {
    uri?: string;
  }

  export interface VideoProps {
    source: VideoSource;
    style?: StyleProp<ViewStyle>;
    resizeMode?: 'contain' | 'cover' | 'stretch' | 'none';
    onEnd?: () => void;
    onError?: (error: OnErrorData) => void;
    onLoad?: (data: OnLoadData) => void;
    controls?: boolean;
    fullscreen?: boolean;
    repeat?: boolean;
    paused?: boolean;
  }

  export default class Video extends React.Component<VideoProps> {}
}
