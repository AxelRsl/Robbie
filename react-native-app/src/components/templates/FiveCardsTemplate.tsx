import React from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ScrollView,
  StyleSheet,
  Dimensions,
} from 'react-native';
import { Icon } from '@/components/ui/Icon';
import { useTheme } from '@/contexts/ThemeContext';
import type { SceneFunction } from '@/types';

const { width: SW, height: SH } = Dimensions.get('window');
const CARD_WIDTH = (SW - 12 * 6) / 5;

interface Props {
  functions: SceneFunction[];
  onPress: (fn: SceneFunction) => void;
}

export const FiveCardsTemplate: React.FC<Props> = ({ functions, onPress }) => {
  const { theme } = useTheme();
  const items = functions.slice(0, 5);

  return (
    <View style={styles.container}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.scrollContent}
      >
        {items.map((fn) => (
          <TouchableOpacity
            key={fn.id}
            style={[styles.card, { backgroundColor: fn.color || theme.colors.primary }]}
            activeOpacity={0.8}
            onPress={() => onPress(fn)}
          >
            <Icon name={fn.icon} size={32} color="#FFFFFF" />
            <Text style={styles.cardTitle} numberOfLines={2}>{fn.name}</Text>
            {fn.description ? (
              <Text style={styles.cardDesc} numberOfLines={3}>{fn.description}</Text>
            ) : null}
          </TouchableOpacity>
        ))}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
  },
  scrollContent: {
    paddingHorizontal: 12,
    gap: 10,
    alignItems: 'stretch',
  },
  card: {
    width: CARD_WIDTH,
    borderRadius: 16,
    padding: 14,
    justifyContent: 'center',
    minHeight: SH * 0.6,
  },
  cardTitle: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '700',
    marginTop: 10,
  },
  cardDesc: {
    color: 'rgba(255,255,255,0.85)',
    fontSize: 9,
    marginTop: 4,
    lineHeight: 13,
  },
});
