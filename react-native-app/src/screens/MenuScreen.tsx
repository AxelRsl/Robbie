import React from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
} from 'react-native';
import { useAppStore } from '@/stores/useAppStore';

interface MenuItem {
  id: string;
  title: string;
  description: string;
  icon: string;
  color: string;
  screen: string;
}

const menuItems: MenuItem[] = [
  {
    id: 'retail',
    title: 'Modo Minorista',
    description: 'Catálogo de productos con búsqueda inteligente',
    icon: '🛍️',
    color: '#00695C',
    screen: 'Retail',
  },
  {
    id: 'promo',
    title: 'Modo Promoción',
    description: 'Videos y ofertas especiales',
    icon: '🎬',
    color: '#FF5722',
    screen: 'Promo',
  },
  {
    id: 'config',
    title: 'Configuracion',
    description: 'Ajustes de conexion a la nube',
    icon: '\u2699\uFE0F',
    color: '#1976D2',
    screen: 'Config',
  },
];

export const MenuScreen: React.FC = () => {
  const { menuTemplate, setMenuTemplate, setCurrentMode } = useAppStore();

  const toggleTemplate = () => {
    setMenuTemplate(menuTemplate === 'classic' ? 'modern' : 'classic');
  };

  const handleMenuPress = (item: MenuItem) => {
    setCurrentMode(item.screen.toLowerCase());
  };

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
          {menuItems.map((item) => (
            <TouchableOpacity
              key={item.id}
              style={[styles.classicButton, { backgroundColor: item.color }]}
              onPress={() => handleMenuPress(item)}
              activeOpacity={0.8}
            >
              <Text style={styles.classicIcon}>{item.icon}</Text>
              <Text style={styles.classicTitle}>{item.title}</Text>
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
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    backgroundColor: '#FFFFFF',
    borderBottomWidth: 1,
    borderBottomColor: '#E0E0E0',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#212121',
  },
  templateButton: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    backgroundColor: '#9C27B0',
    borderRadius: 20,
  },
  templateButtonText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
  },
  classicContainer: {
    flex: 1,
    padding: 16,
    justifyContent: 'center',
  },
  classicButton: {
    height: 120,
    marginVertical: 12,
    borderRadius: 16,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
  },
  classicIcon: {
    fontSize: 48,
    marginBottom: 8,
  },
  classicTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#FFFFFF',
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
