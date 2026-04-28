import React, { useState } from 'react';
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

const { width: SW } = Dimensions.get('window');

interface Props {
  functions: SceneFunction[];
  onPress: (fn: SceneFunction) => void;
}

export const BubbleBottomTemplate: React.FC<Props> = ({ functions, onPress }) => {
  const { theme } = useTheme();
  const [selectedIdx, setSelectedIdx] = useState(0);

  const selected = functions[selectedIdx] || functions[0];
  const secondaryItems = functions.filter((_, i) => i !== selectedIdx).slice(0, 2);

  if (functions.length === 0) return null;

  return (
    <View style={styles.container}>
      {/* Contenido principal - tarjetas grandes */}
      <View style={styles.content}>
        <TouchableOpacity
          style={[styles.mainCard, { backgroundColor: selected?.color || theme.colors.primary }]}
          activeOpacity={0.8}
          onPress={() => selected && onPress(selected)}
        >
          <Icon name={selected?.icon || 'settings'} size={40} color="#FFFFFF" />
          <Text style={styles.mainTitle}>{selected?.name}</Text>
          {selected?.description ? (
            <Text style={styles.mainDesc}>{selected.description}</Text>
          ) : null}
        </TouchableOpacity>

        {secondaryItems.length > 0 && (
          <View style={styles.secondaryRow}>
            {secondaryItems.map((fn) => (
              <TouchableOpacity
                key={fn.id}
                style={[styles.secondaryCard, { backgroundColor: fn.color || theme.colors.secondary }]}
                activeOpacity={0.8}
                onPress={() => onPress(fn)}
              >
                <Icon name={fn.icon} size={28} color="#FFFFFF" />
                <Text style={styles.secondaryTitle} numberOfLines={1}>{fn.name}</Text>
              </TouchableOpacity>
            ))}
          </View>
        )}
      </View>

      {/* Tabs inferiores */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.tabBar}
      >
        {functions.map((fn, idx) => (
          <TouchableOpacity
            key={fn.id}
            style={[
              styles.tab,
              {
                backgroundColor: idx === selectedIdx
                  ? (fn.color || theme.colors.primary)
                  : theme.colors.glass,
                borderColor: idx === selectedIdx
                  ? 'transparent'
                  : theme.colors.glassBorder,
              },
            ]}
            activeOpacity={0.7}
            onPress={() => setSelectedIdx(idx)}
          >
            <Text
              style={[
                styles.tabText,
                { color: idx === selectedIdx ? '#FFFFFF' : theme.colors.onSurface },
              ]}
              numberOfLines={1}
            >
              {fn.name}
            </Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 12,
  },
  content: {
    flex: 1,
    gap: 10,
  },
  mainCard: {
    flex: 2,
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
  secondaryRow: {
    flex: 1,
    flexDirection: 'row',
    gap: 10,
  },
  secondaryCard: {
    flex: 1,
    borderRadius: 16,
    padding: 14,
    justifyContent: 'center',
    alignItems: 'center',
  },
  secondaryTitle: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '600',
    marginTop: 6,
  },
  tabBar: {
    paddingTop: 10,
    paddingBottom: 4,
    gap: 8,
  },
  tab: {
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderRadius: 20,
    borderWidth: 1,
  },
  tabText: {
    fontSize: 11,
    fontWeight: '600',
  },
});
