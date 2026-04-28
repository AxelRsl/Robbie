import React from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Dimensions,
} from 'react-native';
import { Icon } from '@/components/ui/Icon';
import { useTheme } from '@/contexts/ThemeContext';
import type { SceneFunction } from '@/types';

const { width: SW } = Dimensions.get('window');

interface Props {
  functions: SceneFunction[];
  onPress: (fn: SceneFunction) => void;
}

export const FourCardsTemplate: React.FC<Props> = ({ functions, onPress }) => {
  const { theme } = useTheme();
  const items = functions.slice(0, 4);

  return (
    <View style={styles.container}>
      <View style={styles.row}>
        {items.slice(0, 2).map((fn) => (
          <TouchableOpacity
            key={fn.id}
            style={[styles.card, { backgroundColor: fn.color || theme.colors.primary }]}
            activeOpacity={0.8}
            onPress={() => onPress(fn)}
          >
            <Icon name={fn.icon} size={36} color="#FFFFFF" />
            <Text style={styles.cardTitle} numberOfLines={2}>{fn.name}</Text>
            {fn.description ? (
              <Text style={styles.cardDesc} numberOfLines={2}>{fn.description}</Text>
            ) : null}
          </TouchableOpacity>
        ))}
      </View>
      <View style={styles.row}>
        {items.slice(2, 4).map((fn) => (
          <TouchableOpacity
            key={fn.id}
            style={[styles.card, { backgroundColor: fn.color || theme.colors.secondary }]}
            activeOpacity={0.8}
            onPress={() => onPress(fn)}
          >
            <Icon name={fn.icon} size={36} color="#FFFFFF" />
            <Text style={styles.cardTitle} numberOfLines={2}>{fn.name}</Text>
            {fn.description ? (
              <Text style={styles.cardDesc} numberOfLines={2}>{fn.description}</Text>
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
    padding: 12,
    gap: 12,
  },
  row: {
    flex: 1,
    flexDirection: 'row',
    gap: 12,
  },
  card: {
    flex: 1,
    borderRadius: 16,
    padding: 16,
    justifyContent: 'center',
  },
  cardTitle: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '700',
    marginTop: 10,
  },
  cardDesc: {
    color: 'rgba(255,255,255,0.85)',
    fontSize: 10,
    marginTop: 4,
    lineHeight: 14,
  },
});
