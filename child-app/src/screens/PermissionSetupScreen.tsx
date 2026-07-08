import React, {useEffect} from 'react';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {ChildStackParamList} from './RootNavigator';
import {LoadingCenter} from '../components/AppScreen';

/** @deprecated Redirects to PermissionDashboard */
type Props = NativeStackScreenProps<ChildStackParamList, 'PermissionSetup'>;

export function PermissionSetupScreen({navigation}: Props) {
  useEffect(() => {
    navigation.replace('PermissionDashboard');
  }, [navigation]);
  return <LoadingCenter label="Opening permission dashboard…" />;
}
