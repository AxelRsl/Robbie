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

export const FunctionalGridTemplate: React.FC<Props> = ({ functions, onPress }) => {
  const { theme } = useTheme();

  const columns = functions.length <= 4 ? 2
    : functions.length <= 6 ? 3
    : functions.length <= 8 ? 4
    : 5;

  const itemWidth = (SW - 48 - (columns - 1) * 12) / columns;

  return (
    <View style={styles.container}>
      <View style={styles.grid}>
        {functions.map((fn) => (
          <TouchableOpacity
            key={fn.id}
            onPress={() => onPress(fn)}
            activeOpacity={0.7}
            style={[styles.item, { width: itemWidth }]}
          >
            <View
              style={[
                styles.iconCircle,
                { backgroundColor: (fn.color || theme.colors.primary) + '20' },
              ]}
            >
              <Icon name={fn.icon} size={28} color={fn.color || theme.colors.primary} />
            </View>
            <Text
              style={[styles.itemName, { color: theme.colors.onSurface }]}
              numberOfLines={2}
            >
              {fn.name}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
    paddingVertical: 16,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    gap: 12,
  },
  item: {
    alignItems: 'center',
    paddingVertical: 12,
  },
  iconCircle: {
    width: 56,
    height: 56,
    borderRadius: 28,
    justifyContent: 'center',
    alignItems: 'center',
  },
  itemName: {
    fontSize: 10,
    fontWeight: '600',
    textAlign: 'center',
    marginTop: 6,
  },
});
