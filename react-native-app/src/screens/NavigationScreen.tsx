import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ScrollView,
  NativeModules,
} from 'react-native';
import { useAppStore } from '@/stores/useAppStore';
import { useTheme } from '@/contexts/ThemeContext';
import { createStyles, GlobalStyles } from '@/theme/styles';
import { Icon } from '@/components/ui/Icon';

const { RobotSkillModule } = NativeModules;

export const NavigationScreen: React.FC = () => {
  const { navigation, setNavigation, setCurrentMode, startNavigation } = useAppStore();
  const [places, setPlaces] = useState<string[]>([]);
  const [loadingPlaces, setLoadingPlaces] = useState(true);
  const { theme } = useTheme();
  const styles = createStyles(theme);

  useEffect(() => {
    loadPlaces();
  }, []);

  useEffect(() => {
    if (!navigation) return;

    const arrivalTimer = setTimeout(() => {
      setNavigation(null);
    }, 30000);

    return () => clearTimeout(arrivalTimer);
  }, [navigation]);

  const loadPlaces = async () => {
    setLoadingPlaces(true);
    try {
      if (RobotSkillModule) {
        const result = await RobotSkillModule.getMapPlaces();
        setPlaces(result || []);
      }
    } catch (error) {
      console.error('[NavigationScreen] Error loading places:', error);
    } finally {
      setLoadingPlaces(false);
    }
  };

  const handleNavigateTo = async (place: string) => {
    try {
      startNavigation(place);
      if (RobotSkillModule) {
        await RobotSkillModule.executeAction(
          'com.robbie.action.NAVIGATE_TO_LOCATION',
          JSON.stringify({ destination: place })
        );
      }
    } catch (error) {
      console.error('[NavigationScreen] Error navigating:', error);
    }
  };

  const handleStopNavigation = async () => {
    try {
      if (RobotSkillModule) {
        await RobotSkillModule.executeAction(
          'com.robbie.action.STOP_NAVIGATION',
          '{}'
        );
      }
    } catch (error) {
      console.error('[NavigationScreen] Error stopping navigation:', error);
    }
    setNavigation(null);
  };

  // Si esta navegando, mostrar pantalla de navegacion activa
  if (navigation) {
    return (
      <View style={[styles.container, { backgroundColor: theme.colors.background, justifyContent: 'center', alignItems: 'center' }]}>
        <Icon name="navigation" size={32} color={theme.colors.primary} />
        <Text style={{ fontSize: 14, fontWeight: '700', color: theme.colors.onSurface, marginTop: 12 }}>
          {navigation.destination}
        </Text>
        <Text style={{ fontSize: 10, color: theme.colors.onSurfaceVariant, marginTop: 4 }}>
          Navegando...
        </Text>
        <TouchableOpacity
          onPress={handleStopNavigation}
          activeOpacity={0.7}
          style={{ marginTop: 24, paddingHorizontal: 20, paddingVertical: 10, backgroundColor: theme.colors.error, borderRadius: 20 }}
        >
          <Text style={{ color: '#FFF', fontSize: 11, fontWeight: '700' }}>Detener</Text>
        </TouchableOpacity>
      </View>
    );
  }

  // Lista de puntos del mapa
  return (
    <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
      {/* Header minimalista */}
      <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 10 }}>
        <TouchableOpacity onPress={() => setCurrentMode('menu')} activeOpacity={0.7}>
          <Icon name="chevronLeft" size="lg" color={theme.colors.primary} />
        </TouchableOpacity>
        <View style={{ flex: 1 }} />
        <TouchableOpacity onPress={loadPlaces} activeOpacity={0.7}>
          <Icon name="refresh" size="sm" color={theme.colors.onSurfaceVariant} />
        </TouchableOpacity>
      </View>

      {loadingPlaces ? (
        <View style={[GlobalStyles.center, { flex: 1 }]}>
          <Icon name="loading" size="xl" color={theme.colors.primary} />
        </View>
      ) : places.length === 0 ? (
        <View style={[GlobalStyles.center, { flex: 1 }]}>
          <Icon name="map" size="xl" color={theme.colors.onSurfaceVariant} />
          <Text style={{ fontSize: 11, color: theme.colors.onSurfaceVariant, marginTop: 8 }}>
            No hay puntos en el mapa
          </Text>
        </View>
      ) : (
        <ScrollView style={{ flex: 1 }} showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: 16 }}>
          {places.map((place, index) => (
            <TouchableOpacity
              key={index}
              onPress={() => handleNavigateTo(place)}
              activeOpacity={0.7}
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                paddingVertical: 12,
                paddingHorizontal: 12,
                marginBottom: 6,
                backgroundColor: theme.colors.surface,
                borderRadius: 10,
              }}
            >
              <Icon name="location" size="sm" color={theme.colors.primary} />
              <Text style={{ flex: 1, fontSize: 12, fontWeight: '600', color: theme.colors.onSurface, marginLeft: 10 }}>
                {place}
              </Text>
              <Icon name="chevronRight" size="sm" color={theme.colors.onSurfaceVariant} />
            </TouchableOpacity>
          ))}
        </ScrollView>
      )}
    </View>
  );
};
