import React from 'react';
import {
  TouchableOpacity,
  Text,
  ViewStyle,
  TextStyle,
  ActivityIndicator,
  View,
} from 'react-native';
import { useTheme } from '@/contexts/ThemeContext';
import { createStyles } from '@/theme/styles';
import { Icon } from './Icon';

interface ButtonProps {
  title: string;
  onPress: () => void;
  variant?: 'primary' | 'secondary' | 'ghost' | 'gradient';
  size?: 'sm' | 'md' | 'lg';
  disabled?: boolean;
  loading?: boolean;
  icon?: string;
  iconPosition?: 'left' | 'right';
  style?: ViewStyle;
  textStyle?: TextStyle;
}

export const Button: React.FC<ButtonProps> = ({
  title,
  onPress,
  variant = 'primary',
  size = 'md',
  disabled = false,
  loading = false,
  icon,
  iconPosition = 'left',
  style,
  textStyle,
}) => {
  const { theme } = useTheme();
  const styles = createStyles(theme);

  const getSizeStyles = () => {
    switch (size) {
      case 'sm':
        return {
          paddingHorizontal: 12,
          paddingVertical: 8,
          fontSize: 14,
        };
      case 'lg':
        return {
          paddingHorizontal: 24,
          paddingVertical: 16,
          fontSize: 18,
        };
      default:
        return {
          paddingHorizontal: 20,
          paddingVertical: 12,
          fontSize: 16,
        };
    }
  };

  const sizeStyles = getSizeStyles();

  const getButtonStyle = (): ViewStyle => {
    const baseStyle: ViewStyle = {
      borderRadius: 8,
      alignItems: 'center',
      justifyContent: 'center',
      flexDirection: 'row',
      paddingHorizontal: sizeStyles.paddingHorizontal,
      paddingVertical: sizeStyles.paddingVertical,
      opacity: disabled || loading ? 0.6 : 1,
    };

    switch (variant) {
      case 'secondary':
        return {
          ...baseStyle,
          backgroundColor: 'transparent',
          borderWidth: 1,
          borderColor: theme.colors.primary,
        };
      case 'ghost':
        return {
          ...baseStyle,
          backgroundColor: 'transparent',
        };
      case 'gradient':
        return {
          ...baseStyle,
          backgroundColor: 'transparent',
        };
      default:
        return {
          ...baseStyle,
          backgroundColor: theme.colors.primary,
          shadowColor: theme.colors.primary,
          shadowOffset: { width: 0, height: 2 },
          shadowOpacity: 0.3,
          shadowRadius: 4,
          elevation: 3,
        };
    }
  };

  const getTextStyle = (): TextStyle => {
    const baseTextStyle: TextStyle = {
      fontSize: sizeStyles.fontSize,
      fontWeight: '600',
    };

    switch (variant) {
      case 'secondary':
      case 'ghost':
        return {
          ...baseTextStyle,
          color: theme.colors.primary,
        };
      default:
        return {
          ...baseTextStyle,
          color: theme.colors.onPrimary,
        };
    }
  };

  const renderContent = () => (
    <View style={{ flexDirection: 'row', alignItems: 'center' }}>
      {loading && (
        <ActivityIndicator
          size="small"
          color={variant === 'primary' || variant === 'gradient' ? theme.colors.onPrimary : theme.colors.primary}
          style={{ marginRight: icon || title ? 8 : 0 }}
        />
      )}
      {icon && iconPosition === 'left' && !loading && (
        <Icon
          name={icon}
          size="sm"
          color={variant === 'primary' || variant === 'gradient' ? theme.colors.onPrimary : theme.colors.primary}
          style={{ marginRight: 8 }}
        />
      )}
      {title && (
        <Text style={[getTextStyle(), textStyle]}>
          {title}
        </Text>
      )}
      {icon && iconPosition === 'right' && !loading && (
        <Icon
          name={icon}
          size="sm"
          color={variant === 'primary' || variant === 'gradient' ? theme.colors.onPrimary : theme.colors.primary}
          style={{ marginLeft: 8 }}
        />
      )}
    </View>
  );

  if (variant === 'gradient') {
    // Fallback: usar color sólido en lugar de gradiente hasta instalar LinearGradient
    return (
      <TouchableOpacity
        style={[getButtonStyle(), { backgroundColor: theme.colors.primary }, style]}
        onPress={onPress}
        disabled={disabled || loading}
        activeOpacity={0.8}
      >
        {renderContent()}
      </TouchableOpacity>
    );
  }

  return (
    <TouchableOpacity
      style={[getButtonStyle(), style]}
      onPress={onPress}
      disabled={disabled || loading}
      activeOpacity={0.8}
    >
      {renderContent()}
    </TouchableOpacity>
  );
};
