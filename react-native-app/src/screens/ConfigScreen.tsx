import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
  ActivityIndicator,
} from 'react-native';
import { useAppStore } from '@/stores/useAppStore';
import { RobotBridge } from '@/services/RobotBridge';
import type { RobotConfig } from '@/types';

export const ConfigScreen: React.FC = () => {
  const { setCurrentMode } = useAppStore();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [config, setConfig] = useState<RobotConfig | null>(null);

  const [appId, setAppId] = useState('');
  const [appSecret, setAppSecret] = useState('');
  const [clientId, setClientId] = useState('');
  const [apiDomain, setApiDomain] = useState('');
  const [biDomain, setBiDomain] = useState('');
  const [aiOpenDomain, setAiOpenDomain] = useState('');
  const [region, setRegion] = useState('');

  useEffect(() => {
    loadConfig();
  }, []);

  const loadConfig = async () => {
    try {
      setLoading(true);
      const cfg = await RobotBridge.getConfig();
      setConfig(cfg);
      
      setAppId(cfg.cloudConfig.appId);
      setAppSecret(cfg.cloudConfig.appSecret);
      setClientId(cfg.cloudConfig.clientId || '');
      setApiDomain(cfg.cloudConfig.apiDomain);
      setBiDomain(cfg.cloudConfig.biDomain);
      setAiOpenDomain(cfg.cloudConfig.aiOpenDomain);
      setRegion(cfg.cloudConfig.region);
    } catch (error) {
      console.error('Error cargando configuracion:', error);
      Alert.alert('Error', 'No se pudo cargar la configuracion');
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    try {
      setSaving(true);
      
      const updatedConfig: Partial<RobotConfig> = {
        cloudConfig: {
          appId,
          appSecret,
          clientId,
          apiDomain,
          biDomain,
          aiOpenDomain,
          region,
        },
      };

      await RobotBridge.updateConfig(updatedConfig);
      
      Alert.alert(
        'Guardado',
        'Configuracion actualizada. Reinicia la app para aplicar cambios.',
        [{ text: 'OK', onPress: () => setCurrentMode('menu') }]
      );
    } catch (error) {
      console.error('Error guardando configuracion:', error);
      Alert.alert('Error', 'No se pudo guardar la configuracion');
    } finally {
      setSaving(false);
    }
  };

  const handleReset = () => {
    Alert.alert(
      'Restaurar valores por defecto',
      'Esto restaurara las credenciales originales de xiabao. Continuar?',
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Restaurar',
          style: 'destructive',
          onPress: () => {
            setAppId('orion.appid.1581420888108');
            setAppSecret('824416C04CC211EAB2499D538CA28633');
            setClientId('orion.ovs.client.1514259512471');
            setApiDomain('https://global-api-orionbase.orionstar.com');
            setBiDomain('https://global-recv-bi.orionstar.com');
            setAiOpenDomain('https://ai-open.ainirobot.com');
            setRegion('global');
          },
        },
      ]
    );
  };

  if (loading) {
    return (
      <View style={styles.centerContainer}>
        <ActivityIndicator size="large" color="#1976D2" />
        <Text style={styles.loadingText}>Cargando configuracion...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => setCurrentMode('menu')} activeOpacity={0.7}>
          <Text style={styles.backButton}>← Menu</Text>
        </TouchableOpacity>
        <Text style={styles.title}>Configuracion</Text>
        <TouchableOpacity onPress={handleReset} activeOpacity={0.7}>
          <Text style={styles.resetButton}>Restaurar</Text>
        </TouchableOpacity>
      </View>

      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Credenciales OrionStar</Text>
          <Text style={styles.sectionDescription}>
            Estas credenciales se usan para autenticar con la nube OrionStar
          </Text>

          <Text style={styles.label}>App ID</Text>
          <TextInput
            style={styles.input}
            value={appId}
            onChangeText={setAppId}
            placeholder="orion.appid.XXXXXXXXXXXX"
            autoCapitalize="none"
            autoCorrect={false}
          />

          <Text style={styles.label}>App Secret</Text>
          <TextInput
            style={styles.input}
            value={appSecret}
            onChangeText={setAppSecret}
            placeholder="XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            autoCapitalize="none"
            autoCorrect={false}
            secureTextEntry
          />

          <Text style={styles.label}>Client ID</Text>
          <TextInput
            style={styles.input}
            value={clientId}
            onChangeText={setClientId}
            placeholder="orion.ovs.client.XXXXXXXXXXXXX"
            autoCapitalize="none"
            autoCorrect={false}
          />
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Dominios de API</Text>

          <Text style={styles.label}>API OrionBase</Text>
          <TextInput
            style={styles.input}
            value={apiDomain}
            onChangeText={setApiDomain}
            placeholder="https://global-api-orionbase.orionstar.com"
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="url"
          />

          <Text style={styles.label}>AI Open (OAuth)</Text>
          <TextInput
            style={styles.input}
            value={aiOpenDomain}
            onChangeText={setAiOpenDomain}
            placeholder="https://ai-open.ainirobot.com"
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="url"
          />

          <Text style={styles.label}>Region</Text>
          <View style={styles.regionContainer}>
            {['global', 'us', 'jp', 'cn_prod'].map((r) => (
              <TouchableOpacity
                key={r}
                style={[
                  styles.regionButton,
                  region === r && styles.regionButtonActive,
                ]}
                onPress={() => setRegion(r)}
              >
                <Text
                  style={[
                    styles.regionButtonText,
                    region === r && styles.regionButtonTextActive,
                  ]}
                >
                  {r.toUpperCase()}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        <View style={styles.infoBox}>
          <Text style={styles.infoTitle}>Nota importante</Text>
          <Text style={styles.infoText}>
            Las credenciales por defecto son del proyecto xiabao original y pueden no funcionar
            en emuladores o dispositivos no registrados.
          </Text>
          <Text style={styles.infoText}>
            Para obtener credenciales validas, contacta a OrionStar o usa el robot fisico.
          </Text>
        </View>

        <TouchableOpacity
          style={[styles.saveButton, saving && styles.saveButtonDisabled]}
          onPress={handleSave}
          disabled={saving}
          activeOpacity={0.7}
        >
          {saving ? (
            <ActivityIndicator color="#FFFFFF" />
          ) : (
            <Text style={styles.saveButtonText}>Guardar Configuracion</Text>
          )}
        </TouchableOpacity>

        <View style={styles.bottomPadding} />
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
  backButton: {
    fontSize: 16,
    color: '#1976D2',
    fontWeight: '600',
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#212121',
  },
  resetButton: {
    fontSize: 14,
    color: '#F44336',
    fontWeight: '600',
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5F5F5',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 16,
    color: '#757575',
  },
  scrollView: {
    flex: 1,
  },
  section: {
    backgroundColor: '#FFFFFF',
    padding: 16,
    marginTop: 16,
    marginHorizontal: 16,
    borderRadius: 8,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 4,
  },
  sectionDescription: {
    fontSize: 13,
    color: '#757575',
    marginBottom: 16,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: '#424242',
    marginBottom: 6,
    marginTop: 12,
  },
  input: {
    backgroundColor: '#F5F5F5',
    borderWidth: 1,
    borderColor: '#E0E0E0',
    borderRadius: 8,
    padding: 12,
    fontSize: 14,
    color: '#212121',
  },
  regionContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 8,
  },
  regionButton: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#1976D2',
    backgroundColor: '#FFFFFF',
  },
  regionButtonActive: {
    backgroundColor: '#1976D2',
  },
  regionButtonText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#1976D2',
  },
  regionButtonTextActive: {
    color: '#FFFFFF',
  },
  infoBox: {
    backgroundColor: '#FFF3E0',
    padding: 16,
    marginHorizontal: 16,
    marginTop: 16,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: '#FF9800',
  },
  infoTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#E65100',
    marginBottom: 8,
  },
  infoText: {
    fontSize: 13,
    color: '#E65100',
    lineHeight: 18,
    marginBottom: 4,
  },
  saveButton: {
    backgroundColor: '#1976D2',
    padding: 16,
    marginHorizontal: 16,
    marginTop: 24,
    borderRadius: 8,
    alignItems: 'center',
  },
  saveButtonDisabled: {
    opacity: 0.6,
  },
  saveButtonText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#FFFFFF',
  },
  bottomPadding: {
    height: 32,
  },
});
