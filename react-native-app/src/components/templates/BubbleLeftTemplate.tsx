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

const { width: SW, height: SH } = Dimensions.get('window');

interface Props {
  functions: SceneFunction[];
  onPress: (fn: SceneFunction) => void;
}

export const BubbleLeftTemplate: React.FC<Props> = ({ functions, onPress }) => {
  const { theme } = useTheme();
  const [selectedIdx, setSelectedIdx] = useState(0);
  const selected = functions[selectedIdx] || functions[0];

  if (functions.length === 0) return null;

  return (
    <View style={styles.container}>
      {/* Sidebar izquierda con burbujas */}
      <View style={styles.sidebar}>
        <ScrollView showsVerticalScrollIndicator={false}>
          {functions.map((fn, idx) => (
            <TouchableOpacity
              key={fn.id}
              style={[
                styles.bubble,
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
                  styles.bubbleText,
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

      {/* Contenido principal derecha */}
      <View style={styles.content}>
        {selected && (
          <TouchableOpacity
            style={[styles.contentCard, { backgroundColor: selected.color || theme.colors.primary }]}
            activeOpacity={0.8}
            onPress={() => onPress(selected)}
          >
            <Icon name={selected.icon} size={48} color="#FFFFFF" />
            <Text style={styles.contentTitle}>{selected.name}</Text>
            {selected.description ? (
              <Text style={styles.contentDesc}>{selected.description}</Text>
            ) : null}
            <View style={styles.goButton}>
              <Text style={styles.goButtonText}>Abrir</Text>
              <Icon name="chevronRight" size={16} color="#FFFFFF" />
            </View>
          </TouchableOpacity>
        )}
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
  sidebar: {
    width: SW * 0.25,
  },
  bubble: {
    paddingVertical: 10,
    paddingHorizontal: 14,
    borderRadius: 20,
    marginBottom: 8,
    borderWidth: 1,
  },
  bubbleText: {
    fontSize: 11,
    fontWeight: '600',
    textAlign: 'center',
  },
  content: {
    flex: 1,
  },
  contentCard: {
    flex: 1,
    borderRadius: 16,
    padding: 24,
    justifyContent: 'center',
  },
  contentTitle: {
    color: '#FFFFFF',
    fontSize: 20,
    fontWeight: '700',
    marginTop: 16,
  },
  contentDesc: {
    color: 'rgba(255,255,255,0.85)',
    fontSize: 13,
    marginTop: 8,
    lineHeight: 18,
  },
  goButton: {
    flexDirection: 'row',
    alignItems: 'center',
    alignSelf: 'flex-start',
    backgroundColor: 'rgba(255,255,255,0.25)',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    marginTop: 16,
  },
  goButtonText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '600',
    marginRight: 4,
  },
});
