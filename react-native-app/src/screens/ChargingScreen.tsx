import React from 'react';
import { View } from 'react-native';

/**
 * ChargingScreen — Minimal dark backdrop shown while robot charges.
 *
 * The sleeping mascot animation (FaceOverlay) renders on top.
 * The "Dejar de cargar" button is rendered in App.tsx above FaceOverlay.
 */
export const ChargingScreen: React.FC = () => {
  return (
    <View style={{ flex: 1, backgroundColor: '#060d0f' }} />
  );
};
