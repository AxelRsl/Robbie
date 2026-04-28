import React from 'react';
import { TextStyle } from 'react-native';
// @ts-ignore
import MaterialIcons from 'react-native-vector-icons/MaterialIcons';
import { IconSizes, getIcon } from '@/theme/icons';
import { useTheme } from '@/contexts/ThemeContext';

interface IconProps {
  name: string;
  size?: keyof typeof IconSizes | number;
  color?: string;
  style?: TextStyle;
}

export const Icon: React.FC<IconProps> = ({
  name,
  size = 'md',
  color,
  style,
}) => {
  const { theme } = useTheme();
  
  const iconName = getIcon(name as any);
  const iconSize = typeof size === 'number' ? size : IconSizes[size];
  const iconColor = color || theme.colors.onSurface;

  return (
    <MaterialIcons
      name={iconName}
      size={iconSize}
      color={iconColor}
      style={style}
    />
  );
};

// Componente limpio solo con MaterialIcons
