import React from 'react';
import { View, ViewStyle, StyleSheet } from 'react-native';
import { useTheme } from '@/contexts/ThemeContext';

interface GlassPanelProps {
  children: React.ReactNode;
  style?: ViewStyle;
  borderRadius?: number;
  padding?: number;
  elevation?: number;
}

export const GlassPanel: React.FC<GlassPanelProps> = ({
  children,
  style,
  borderRadius = 12,
  padding = 16,
  elevation = 4,
}) => {
  const { theme } = useTheme();

  return (
    <View
      style={[
        styles.container,
        {
          padding,
          borderRadius,
          borderWidth: 1,
          borderColor: theme.colors.glassBorder,
          backgroundColor: theme.colors.glass,
          shadowColor: theme.isDark ? '#000' : theme.colors.primary,
          shadowOffset: { width: 0, height: 4 },
          shadowOpacity: theme.isDark ? 0.3 : 0.1,
          shadowRadius: 8,
          elevation,
        },
        style,
      ]}
    >
      {children}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    // Base styles handled by dynamic styling above
  },
});
