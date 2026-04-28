import React, { useRef, useEffect, useState } from 'react';
import {
  View,
  Text,
  FlatList,
  Image,
  TouchableOpacity,
  Dimensions,
  StyleSheet,
} from 'react-native';
import { useAppStore } from '@/stores/useAppStore';
import { useTheme } from '@/contexts/ThemeContext';
import { createStyles } from '@/theme/styles';
import { Icon } from '@/components/ui/Icon';
import type { Product } from '@/types';

const { width: SCREEN_WIDTH } = Dimensions.get('window');
const CARD_WIDTH = SCREEN_WIDTH * 0.35;
const CARD_MARGIN = 8;

export const HomeScreen: React.FC = () => {
  const { products, productsLoaded, setCurrentMode, setSelectedProduct } = useAppStore();
  const { theme } = useTheme();
  const styles = createStyles(theme);
  const flatListRef = useRef<FlatList>(null);
  const [currentIndex, setCurrentIndex] = useState(0);

  // Productos destacados (max 8 para el carrusel)
  const featuredProducts = products.slice(0, 8);

  // Auto-scroll del carrusel
  useEffect(() => {
    if (featuredProducts.length <= 1) return;
    const interval = setInterval(() => {
      setCurrentIndex((prev) => {
        const next = (prev + 1) % featuredProducts.length;
        flatListRef.current?.scrollToIndex({ index: next, animated: true });
        return next;
      });
    }, 4000);
    return () => clearInterval(interval);
  }, [featuredProducts.length]);

  const handleProductPress = (product: Product) => {
    setSelectedProduct(product);
    setCurrentMode('retail');
  };

  const renderProductCard = ({ item }: { item: Product }) => (
    <TouchableOpacity
      activeOpacity={0.8}
      onPress={() => handleProductPress(item)}
      style={localStyles.card}
    >
      <Image
        source={{ uri: item.imageUrl }}
        style={localStyles.cardImage}
        resizeMode="cover"
      />
      <View style={localStyles.cardContent}>
        <Text style={[localStyles.cardName, { color: theme.colors.onSurface }]} numberOfLines={1}>
          {item.name}
        </Text>
        <Text style={[localStyles.cardPrice, { color: theme.colors.primary }]}>
          ${item.price.toLocaleString()}
        </Text>
      </View>
    </TouchableOpacity>
  );

  return (
    <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
      {/* Carrusel horizontal de productos */}
      {productsLoaded && featuredProducts.length > 0 ? (
        <>
          <View style={{ flex: 1, justifyContent: 'center' }}>
            <FlatList
              ref={flatListRef}
              data={featuredProducts}
              keyExtractor={(item) => item.id}
              horizontal
              showsHorizontalScrollIndicator={false}
              snapToInterval={CARD_WIDTH + CARD_MARGIN * 2}
              decelerationRate="fast"
              contentContainerStyle={{ paddingHorizontal: 16, paddingVertical: 16 }}
              renderItem={renderProductCard}
              onScrollToIndexFailed={() => {}}
            />
          </View>
          {/* Indicadores */}
          <View style={localStyles.indicators}>
            {featuredProducts.map((_, i) => (
              <View
                key={i}
                style={[
                  localStyles.dot,
                  {
                    backgroundColor: i === currentIndex
                      ? theme.colors.primary
                      : theme.colors.onSurfaceVariant + '40',
                  },
                ]}
              />
            ))}
          </View>
        </>
      ) : (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <Icon name="loading" size="xl" color={theme.colors.primary} />
          <Text style={[styles.body, { marginTop: 12, color: theme.colors.onSurfaceVariant }]}>
            Cargando productos...
          </Text>
        </View>
      )}

      {/* Acceso rapido al menu */}
      <TouchableOpacity
        activeOpacity={0.7}
        onPress={() => setCurrentMode('menu')}
        style={[localStyles.menuButton, { borderColor: theme.colors.primary + '30' }]}
      >
        <Icon name="layers" size="sm" color={theme.colors.primary} />
        <Text style={[localStyles.menuButtonText, { color: theme.colors.primary }]}>
          Menu
        </Text>
      </TouchableOpacity>
    </View>
  );
};

const localStyles = StyleSheet.create({
  card: {
    width: CARD_WIDTH,
    marginHorizontal: CARD_MARGIN,
    borderRadius: 12,
    backgroundColor: '#FFFFFF',
    overflow: 'hidden',
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  cardImage: {
    width: '100%',
    height: CARD_WIDTH * 0.75,
    backgroundColor: '#F0F0F0',
  },
  cardContent: {
    padding: 8,
  },
  cardName: {
    fontSize: 11,
    fontWeight: '600',
    marginBottom: 2,
  },
  cardPrice: {
    fontSize: 13,
    fontWeight: '700',
  },
  indicators: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginTop: 16,
  },
  dot: {
    width: 6,
    height: 4,
    borderRadius: 3,
    marginHorizontal: 3,
  },
  menuButton: {
    flexDirection: 'row',
    alignItems: 'center',
    alignSelf: 'center',
    marginTop: 24,
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    borderWidth: 1,
  },
  menuButtonText: {
    fontSize: 11,
    fontWeight: '600',
    marginLeft: 6,
  },
});
