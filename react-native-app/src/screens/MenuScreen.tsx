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

interface MenuItem {
  id: string;
  title: string;
  description: string;
  icon: string;
  color: string;
  screen: string;
}

// Definición completa de todas las pantallas disponibles
const allMenuItems: MenuItem[] = [
  {
    id: 'retail',
    title: 'Productos',
    description: 'Catálogo de productos',
    icon: '🛍️',
    color: '#00695C',
    screen: 'Retail',
  },
  {
    id: 'promo',
    title: 'Promociones',
    description: 'Ofertas especiales',
    icon: '🎬',
    color: '#FF5722',
    screen: 'Promo',
  },
  {
    id: 'search',
    title: 'Buscar',
    description: 'Búsqueda por voz',
    icon: '🔍',
    color: '#9C27B0',
    screen: 'Search',
  },
  {
    id: 'favorites',
    title: 'Favoritos',
    description: 'Productos guardados',
    icon: '⭐',
    color: '#F57C00',
    screen: 'Favorites',
  },
  {
    id: 'cart',
    title: 'Carrito',
    description: 'Ver carrito',
    icon: '🛒',
    color: '#0288D1',
    screen: 'Cart',
  },
  {
    id: 'orders',
    title: 'Pedidos',
    description: 'Historial',
    icon: '📦',
    color: '#5E35B1',
    screen: 'Orders',
  },
  {
    id: 'help',
    title: 'Ayuda',
    description: 'Asistencia',
    icon: '❓',
    color: '#00897B',
    screen: 'Help',
  },
  {
    id: 'config',
    title: 'Configuración',
    description: 'Ajustes',
    icon: '\u2699\uFE0F',
    color: '#1976D2',
    screen: 'Config',
  },
];

export const MenuScreen: React.FC = () => {
  const { menuTemplate, setMenuTemplate, setCurrentMode } = useAppStore();
  const [menuItems, setMenuItems] = useState<MenuItem[]>(allMenuItems);
  const [loading, setLoading] = useState(true);

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
    } catch (error) {
      console.error('[MenuScreen] Error cargando configuración:', error);
      // En caso de error, mostrar todas las pantallas
      setMenuItems(allMenuItems);
    } finally {
      setLoading(false);
    }
  };

  const toggleTemplate = () => {
    setMenuTemplate(menuTemplate === 'classic' ? 'modern' : 'classic');
  };

  const handleMenuPress = (item: MenuItem) => {
    setCurrentMode(item.screen.toLowerCase());
  };

  if (loading) {
    return (
      <View style={styles.container}>
        <Text style={styles.loadingText}>Cargando menú...</Text>
      </View>
    );
  }

  if (menuTemplate === 'classic') {
    return (
      <View style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.title}>Menú Principal</Text>
          <TouchableOpacity style={styles.templateButton} onPress={toggleTemplate}>
            <Text style={styles.templateButtonText}>⊞ Moderno</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.classicContainer}>
          {menuItems.map((item: MenuItem) => (
            <TouchableOpacity
              key={item.id}
              style={[styles.classicButton, { backgroundColor: item.color }]}
              onPress={() => handleMenuPress(item)}
              activeOpacity={0.8}
            >
              <Text style={styles.classicIcon}>{item.icon}</Text>
              <Text style={styles.classicTitle} numberOfLines={1}>{item.title}</Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Menú Principal</Text>
        <TouchableOpacity style={styles.templateButton} onPress={toggleTemplate}>
          <Text style={styles.templateButtonText}>☰ Clásico</Text>
        </TouchableOpacity>
      </View>

      <ScrollView style={styles.modernContainer}>
        {menuItems.map((item) => (
          <TouchableOpacity
            key={item.id}
            style={styles.modernCard}
            onPress={() => handleMenuPress(item)}
            activeOpacity={0.7}
          >
            <View style={[styles.modernIconContainer, { backgroundColor: item.color }]}>
              <Text style={styles.modernIcon}>{item.icon}</Text>
            </View>
            <View style={styles.modernContent}>
              <Text style={styles.modernTitle}>{item.title}</Text>
              <Text style={styles.modernDescription}>{item.description}</Text>
            </View>
            <Text style={styles.modernArrow}>→</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  loadingText: {
    fontSize: 16,
    color: '#757575',
    textAlign: 'center',
    marginTop: 20,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
    backgroundColor: '#FFFFFF',
    borderBottomWidth: 1,
    borderBottomColor: '#E0E0E0',
  },
  title: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#212121',
  },
  templateButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    backgroundColor: '#9C27B0',
    borderRadius: 16,
  },
  templateButtonText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '600',
  },
  classicContainer: {
    flex: 1,
    flexDirection: 'row',
    flexWrap: 'wrap',
    padding: 8,
    justifyContent: 'space-between',
    alignContent: 'center',
  },
  classicButton: {
    width: '23.5%',
    aspectRatio: 1,
    marginVertical: 4,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 3,
  },
  classicIcon: {
    fontSize: 28,
    marginBottom: 4,
  },
  classicTitle: {
    fontSize: 11,
    fontWeight: 'bold',
    color: '#FFFFFF',
    textAlign: 'center',
  },
  modernContainer: {
    flex: 1,
    padding: 16,
  },
  modernCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  modernIconContainer: {
    width: 60,
    height: 60,
    borderRadius: 30,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },
  modernIcon: {
    fontSize: 32,
  },
  modernContent: {
    flex: 1,
  },
  modernTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 4,
  },
  modernDescription: {
    fontSize: 13,
    color: '#757575',
  },
  modernArrow: {
    fontSize: 24,
    color: '#BDBDBD',
  },
});
