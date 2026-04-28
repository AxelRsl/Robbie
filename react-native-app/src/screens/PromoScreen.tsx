import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ScrollView,
  Dimensions,
  Image,
} from 'react-native';
import { CloudApi } from '@/services/CloudApi';
import { useAppStore } from '@/stores/useAppStore';
import { useTheme } from '@/contexts/ThemeContext';
import { createStyles, GlobalStyles } from '@/theme/styles';
import { Icon } from '@/components/ui/Icon';
import type { Promotion, PromoVideo } from '@/types';

const { width } = Dimensions.get('window');

export const PromoScreen: React.FC = () => {
  const [promotions, setPromotions] = useState<Promotion[]>([]);
  const [videos, setVideos] = useState<PromoVideo[]>([]);
  const [loading, setLoading] = useState(true);
  const [currentVideoIndex, setCurrentVideoIndex] = useState(0);
  const { theme } = useTheme();
  const themeStyles = createStyles(theme);
  
  const { setCurrentMode } = useAppStore();

  useEffect(() => {
    loadPromotions();
  }, []);

  const loadPromotions = async () => {
    setLoading(true);
    try {
      const [promoData, videoData] = await Promise.all([
        CloudApi.getPromotions(),
        CloudApi.getPromoVideos(),
      ]);
      setPromotions(promoData);
      setVideos(videoData);
    } catch (error) {
      console.error('Error loading promotions:', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <View style={[themeStyles.container, GlobalStyles.center]}>
        <Icon name="loading" size="xl" color={theme.colors.primary} />
        <Text style={[themeStyles.body, { marginTop: 12 }]}>Cargando...</Text>
      </View>
    );
  }

  return (
    <View style={[themeStyles.container, { backgroundColor: theme.colors.background }]}>
      {/* Header minimalista */}
      <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 10 }}>
        <TouchableOpacity onPress={() => setCurrentMode('menu')} activeOpacity={0.7}>
          <Icon name="chevronLeft" size="lg" color={theme.colors.primary} />
        </TouchableOpacity>
        <View style={{ flex: 1 }} />
      </View>

      {/* Video destacado */}
      {videos.length > 0 && (
        <View style={{ backgroundColor: '#000' }}>
          <Image
            source={{ uri: videos[currentVideoIndex].thumbnailUrl }}
            style={{ width, height: width * (9 / 16) }}
            resizeMode="cover"
          />
          <View style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, justifyContent: 'center', alignItems: 'center' }}>
            <Text style={{ fontSize: 36, color: 'rgba(255,255,255,0.8)' }}>▶</Text>
          </View>
        </View>
      )}

      {/* Info video */}
      {videos.length > 0 && (
        <View style={{ padding: 12, flexDirection: 'row', alignItems: 'center' }}>
          <View style={{ flex: 1 }}>
            <Text style={{ fontSize: 12, fontWeight: '700', color: theme.colors.onSurface }}>{videos[currentVideoIndex].title}</Text>
            <Text style={{ fontSize: 10, color: theme.colors.onSurfaceVariant, marginTop: 2 }}>{videos[currentVideoIndex].description}</Text>
          </View>
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            <TouchableOpacity
              onPress={() => setCurrentVideoIndex(Math.max(0, currentVideoIndex - 1))}
              disabled={currentVideoIndex === 0}
              style={{ opacity: currentVideoIndex === 0 ? 0.3 : 1, padding: 6 }}
            >
              <Icon name="chevronLeft" size="sm" color={theme.colors.primary} />
            </TouchableOpacity>
            <Text style={{ fontSize: 10, color: theme.colors.onSurfaceVariant, marginHorizontal: 4 }}>
              {currentVideoIndex + 1}/{videos.length}
            </Text>
            <TouchableOpacity
              onPress={() => setCurrentVideoIndex(Math.min(videos.length - 1, currentVideoIndex + 1))}
              disabled={currentVideoIndex === videos.length - 1}
              style={{ opacity: currentVideoIndex === videos.length - 1 ? 0.3 : 1, padding: 6 }}
            >
              <Icon name="chevronRight" size="sm" color={theme.colors.primary} />
            </TouchableOpacity>
          </View>
        </View>
      )}

      {/* Promociones */}
      <ScrollView style={{ flex: 1 }} showsVerticalScrollIndicator={false}>
        {promotions.map((promo) => (
          <View key={promo.id} style={{ marginHorizontal: 12, marginBottom: 10, padding: 12, backgroundColor: theme.colors.surface, borderRadius: 10 }}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
              <Text style={{ flex: 1, fontSize: 12, fontWeight: '700', color: theme.colors.onSurface }}>{promo.title}</Text>
              <View style={{ backgroundColor: theme.colors.primary, paddingHorizontal: 8, paddingVertical: 3, borderRadius: 10 }}>
                <Text style={{ color: '#FFF', fontSize: 10, fontWeight: '700' }}>{promo.discount}% OFF</Text>
              </View>
            </View>
            <Text style={{ fontSize: 10, color: theme.colors.onSurfaceVariant, marginBottom: 6 }}>{promo.description}</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false}>
              {promo.products.map((product) => (
                <View key={product.id} style={{ width: 80, padding: 6, marginRight: 6, backgroundColor: theme.colors.background, borderRadius: 6 }}>
                  <Text style={{ fontSize: 9, fontWeight: '600', color: theme.colors.onSurface }} numberOfLines={2}>{product.name}</Text>
                  <Text style={{ fontSize: 10, fontWeight: '700', color: theme.colors.primary, marginTop: 2 }}>${product.price.toLocaleString()}</Text>
                </View>
              ))}
            </ScrollView>
          </View>
        ))}
      </ScrollView>
    </View>
  );
};

// Estilos vienen del sistema de temas
