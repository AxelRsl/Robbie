import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
} from 'react-native';
import { useAppStore } from '@/stores/useAppStore';
import { CloudApi } from '@/services/CloudApi';
import { useTheme } from '@/contexts/ThemeContext';
import { createStyles, GlobalStyles } from '@/theme/styles';
import { Icon } from '@/components/ui/Icon';

interface MenuItem {
  id: string;
  title: string;
  description: string;
  icon: string;
  color: string;
  screen: string;
  serverTitle?: string;
  serverDescription?: string;
}

// Menú principal - títulos y descripciones vendrán del servidor
const allMenuItems: MenuItem[] = [
  {
    id: 'retail',
    title: 'Productos', // Fallback
    description: 'Catálogo de productos', // Fallback
    icon: 'shoppingCart',
    color: '#E4027C', // Color primario del panel
    screen: 'Retail',
  },
  {
    id: 'promo',
    title: 'Promociones', // Fallback
    description: 'Ofertas especiales', // Fallback
    icon: 'sparkles',
    color: '#00BFA5', // Color accent del panel
    screen: 'Promo',
  },
  {
    id: 'navigate',
    title: 'Navegar', // Fallback
    description: 'Ir a ubicación', // Fallback
    icon: 'navigation',
    color: '#F472B6', // Rosa claro del panel
    screen: 'Navigate',
  },
  {
    id: 'search',
    title: 'Buscar', // Fallback
    description: 'Búsqueda por voz', // Fallback
    icon: 'search',
    color: '#10B981', // Verde del panel
    screen: 'Search',
  },
];

export const MenuScreen: React.FC = () => {
  const { setCurrentMode, setUiConfig } = useAppStore();
  const [menuItems, setMenuItems] = useState<MenuItem[]>(allMenuItems);
  const [loading, setLoading] = useState(true);
  const { theme } = useTheme();
  const styles = createStyles(theme);
  
  // Template fijo en moderno (sin botón de cambio)
  const menuTemplate = 'modern';

  useEffect(() => {
    loadValidScreens();
  }, []);

  const loadValidScreens = async () => {
    try {
      const config = await CloudApi.getConfig();
      console.log('[MenuScreen] Configuración cargada:', config);
      
      if (config.validScreens && config.validScreens.length > 0) {
        // Filtrar pantallas basado en validScreens de la API
        const filtered = allMenuItems.filter(item => 
          config.validScreens.includes(item.screen)
        );
        console.log('[MenuScreen] Pantallas filtradas:', filtered.length, 'de', allMenuItems.length);
        setMenuItems(filtered);
      } else {
        // Si no hay validScreens, mostrar todas
        console.log('[MenuScreen] No hay validScreens, mostrando todas');
        setMenuItems(allMenuItems);
      }

      if (config.uiConfig) {
        setUiConfig(config.uiConfig);
        console.log('[MenuScreen] uiConfig aplicado:', JSON.stringify(config.uiConfig));
      }
    } catch (error) {
      console.error('[MenuScreen] Error cargando configuración:', error);
      // En caso de error, mostrar todas las pantallas
      setMenuItems(allMenuItems);
    } finally {
      setLoading(false);
    }
  };

  // Template fijo - no se puede cambiar

  const handleMenuPress = (item: MenuItem) => {
    if (item.screen === 'Navigate') {
      setCurrentMode('navigating');
    } else {
      setCurrentMode(item.screen.toLowerCase());
    }
  };

  if (loading) {
    return (
      <View style={[styles.container, GlobalStyles.center]}>
        <Icon name="loading" size="xl" color={theme.colors.primary} />
        <Text style={[styles.body, { marginTop: 16 }]}>Cargando...</Text>
      </View>
    );
  }

  // Solo template moderno - minimalista

  return (
    <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
      {/* Grid minimalista - 4 columnas, sin bordes, con margen generoso */}
      <View style={{ flex: 1, flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-evenly', alignContent: 'center', paddingHorizontal: 24, paddingVertical: 24 }}>
        {menuItems.map((item: MenuItem) => (
          <TouchableOpacity
            key={item.id}
            onPress={() => handleMenuPress(item)}
            activeOpacity={0.7}
            style={{
              width: '22%',
              aspectRatio: 1,
              marginBottom: 20,
              marginHorizontal: 4,
              justifyContent: 'center',
              alignItems: 'center',
              backgroundColor: 'transparent',
            }}
          >
            <Icon name={item.icon} size="lg" color={item.color} />
            <Text style={{ fontSize: 9, fontWeight: '600', color: theme.colors.onSurface, marginTop: 6, textAlign: 'center' }} numberOfLines={1}>
              {item.title}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );
};

// Los estilos ahora vienen del sistema de temas
// Solo definimos estilos específicos de esta pantalla
const localStyles = StyleSheet.create({
  // Estilos específicos del MenuScreen si son necesarios
});
