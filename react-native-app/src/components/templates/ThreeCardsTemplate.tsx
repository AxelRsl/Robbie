import React from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ImageBackground,
  StyleSheet,
  Dimensions,
} from 'react-native';
import { Icon } from '@/components/ui/Icon';
import { useTheme } from '@/contexts/ThemeContext';
import type { SceneFunction } from '@/types';

const { width: SW, height: SH } = Dimensions.get('window');

interface Props {
  functions: SceneFunction[];
  onPress: (fn: SceneFunction) => void;
}

export const ThreeCardsTemplate: React.FC<Props> = ({ functions, onPress }) => {
  const { theme } = useTheme();
  const items = functions.slice(0, 3);
  const main = items[0];
  const secondary = items.slice(1, 3);

  if (!main) return null;

  return (
    <View style={styles.container}>
      {/* Tarjeta principal grande a la izquierda */}
      <TouchableOpacity
        style={[styles.mainCard, { backgroundColor: main.color || theme.colors.primary }]}
        activeOpacity={0.8}
        onPress={() => onPress(main)}
      >
        <Icon name={main.icon} size={48} color="#FFFFFF" />
        <Text style={styles.mainTitle} numberOfLines={2}>{main.name}</Text>
        {main.description ? (
          <Text style={styles.mainDesc} numberOfLines={3}>{main.description}</Text>
        ) : null}
      </TouchableOpacity>

      {/* Dos tarjetas a la derecha */}
      <View style={styles.rightColumn}>
        {secondary.map((fn) => (
          <TouchableOpacity
            key={fn.id}
            style={[styles.secondaryCard, { backgroundColor: fn.color || theme.colors.secondary }]}
            activeOpacity={0.8}
            onPress={() => onPress(fn)}
          >
            <Icon name={fn.icon} size={32} color="#FFFFFF" />
            <Text style={styles.secondaryTitle} numberOfLines={2}>{fn.name}</Text>
            {fn.description ? (
              <Text style={styles.secondaryDesc} numberOfLines={2}>{fn.description}</Text>
            ) : null}
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    flexDirection: 'row',
    padding: 12,
    gap: 12,
  },
  mainCard: {
    flex: 1,
    borderRadius: 16,
    padding: 20,
    justifyContent: 'center',
  },
  mainTitle: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: '700',
    marginTop: 12,
  },
  mainDesc: {
    color: 'rgba(255,255,255,0.85)',
    fontSize: 12,
    marginTop: 6,
    lineHeight: 16,
  },
  rightColumn: {
    flex: 1,
    gap: 12,
  },
  secondaryCard: {
    flex: 1,
    borderRadius: 16,
    padding: 16,
    justifyContent: 'center',
  },
  secondaryTitle: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
    marginTop: 8,
  },
  secondaryDesc: {
    color: 'rgba(255,255,255,0.8)',
    fontSize: 10,
    marginTop: 4,
    lineHeight: 14,
  },
});
