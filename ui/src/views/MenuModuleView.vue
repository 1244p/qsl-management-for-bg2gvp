<script setup lang="ts">
import { computed, type Component } from 'vue'
import type { QslMenuModule } from '../constants/menu-modules'
import QslModuleFrame from '../components/qsl/QslModuleFrame.vue'
import AddressManagementModule from './modules/AddressManagementModule.vue'
import AuditLogModule from './modules/AuditLogModule.vue'
import BureauManagementModule from './modules/BureauManagementModule.vue'
import CardIssueModule from './modules/CardIssueModule.vue'
import CardMutationModule from './modules/CardMutationModule.vue'
import CardQueryModule from './modules/CardQueryModule.vue'
import CardRecordModule from './modules/CardRecordModule.vue'
import DefaultModulePlaceholder from './modules/DefaultModulePlaceholder.vue'
import EquipmentCatalogModule from './modules/EquipmentCatalogModule.vue'
import ExchangeRequestReviewModule from './modules/ExchangeRequestReviewModule.vue'
import ImportExportModule from './modules/ImportExportModule.vue'
import LogSyncModule from './modules/LogSyncModule.vue'
import MailReceiveConfirmModule from './modules/MailReceiveConfirmModule.vue'
import MailSendConfirmModule from './modules/MailSendConfirmModule.vue'
import OverviewDashboardModule from './modules/OverviewDashboardModule.vue'
import OfflineActivityModule from './modules/OfflineActivityModule.vue'
import OnlineImportDataModule from './modules/OnlineImportDataModule.vue'
import QsoQueryModule from './modules/QsoQueryModule.vue'
import QslCardRequestReviewModule from './modules/QslCardRequestReviewModule.vue'
import ReceiveRecordQueryModule from './modules/ReceiveRecordQueryModule.vue'
import ReceiptConfirmModule from './modules/ReceiptConfirmModule.vue'
import QsoRecordModule from './modules/QsoRecordModule.vue'
import ReportAuditlogModule from './modules/ReportAuditlogModule.vue'
import StationCardModule from './modules/StationCardModule.vue'
import StationEquipmentModule from './modules/StationEquipmentModule.vue'
import StationProfileModule from './modules/StationProfileModule.vue'
import SystemSettingsModule from './modules/SystemSettingsModule.vue'

interface ModuleRenderer {
  component: Component
  props?: Record<string, unknown>
}

type SceneType = 'QSO' | 'SWL' | 'ONLINE_EYEBALL' | 'EYEBALL'

const props = defineProps<{
  qslModule: QslMenuModule
}>()

const currentModule = computed(() => props.qslModule)

const renderer = computed<ModuleRenderer>(() => {
  switch (currentModule.value.key) {
    case 'overview-dashboard':
      return { component: OverviewDashboardModule }
    case 'system-settings':
      return { component: SystemSettingsModule }
    case 'station-profile':
      return { component: StationProfileModule }
    case 'station-equipment':
      return { component: StationEquipmentModule }
    case 'station-card':
      return { component: StationCardModule }
    case 'qso-record':
    case 'comm-qso-record':
      return {
        component: QsoRecordModule,
        props: {
          sceneTypes: ['QSO', 'SWL'] as SceneType[],
          defaultSceneType: 'QSO' as SceneType,
        },
      }
    case 'card-record':
      return {
        component: CardRecordModule,
        props: {
          sceneTypes: ['QSO', 'SWL'] as SceneType[],
          defaultSceneType: 'QSO' as SceneType,
        },
      }
    case 'qsl-card-request':
      return { component: QslCardRequestReviewModule }
    case 'online-card-record':
      return {
        component: CardRecordModule,
        props: {
          sceneTypes: ['ONLINE_EYEBALL'] as SceneType[],
          defaultSceneType: 'ONLINE_EYEBALL' as SceneType,
        },
      }
    case 'offline-card-record':
      return {
        component: CardRecordModule,
        props: {
          sceneTypes: ['EYEBALL'] as SceneType[],
          defaultSceneType: 'EYEBALL' as SceneType,
        },
      }
    case 'card-issue':
      return {
        component: CardIssueModule,
        props: {
          sceneTypes: ['QSO', 'SWL'] as SceneType[],
          defaultSceneType: 'QSO' as SceneType,
        },
      }
    case 'online-card-issue':
      return {
        component: CardIssueModule,
        props: {
          sceneTypes: ['ONLINE_EYEBALL'] as SceneType[],
          defaultSceneType: 'ONLINE_EYEBALL' as SceneType,
        },
      }
    case 'offline-card-issue':
      return {
        component: CardIssueModule,
        props: {
          sceneTypes: ['EYEBALL'] as SceneType[],
          defaultSceneType: 'EYEBALL' as SceneType,
        },
      }
    case 'card-mutation':
      return { component: CardMutationModule }
    case 'mail-send-confirm':
      return {
        component: MailSendConfirmModule,
        props: {
          sceneTypes: ['QSO', 'SWL'] as SceneType[],
          defaultSceneType: 'QSO' as SceneType,
        },
      }
    case 'online-mail-send-confirm':
      return {
        component: MailSendConfirmModule,
        props: {
          sceneTypes: ['ONLINE_EYEBALL'] as SceneType[],
          defaultSceneType: 'ONLINE_EYEBALL' as SceneType,
        },
      }
    case 'mail-receive-confirm':
      return {
        component: MailReceiveConfirmModule,
        props: {
          sceneTypes: ['QSO', 'SWL'] as SceneType[],
          defaultSceneType: 'QSO' as SceneType,
          defaultCardType: 'QSO',
          showReceivedRecordMigration: false,
          hideNoSendCardRecords: true,
        },
      }
    case 'online-delivery-confirm':
      return {
        component: ReceiptConfirmModule,
        props: {
          sceneTypes: ['ONLINE_EYEBALL'] as SceneType[],
        },
      }
    case 'offline-delivery-confirm':
      return {
        component: MailReceiveConfirmModule,
        props: {
          sceneTypes: ['EYEBALL'] as SceneType[],
          defaultSceneType: 'EYEBALL' as SceneType,
          defaultCardType: 'EYEBALL',
          hideReceivedMailActions: true,
          hideNoSendCardRecords: true,
        },
      }
    case 'receive-qso':
      return {
        component: MailReceiveConfirmModule,
        props: {
          sceneTypes: ['QSO', 'SWL'] as SceneType[],
          defaultSceneType: 'QSO' as SceneType,
          defaultCardType: 'QSO',
          showReceivedRecordMigration: true,
          showClosedReceiveRows: true,
        },
      }
    case 'receive-online-eyeball':
      return {
        component: MailReceiveConfirmModule,
        props: {
          sceneTypes: ['ONLINE_EYEBALL'] as SceneType[],
          defaultSceneType: 'ONLINE_EYEBALL' as SceneType,
          defaultCardType: 'EYEBALL',
          showReceivedRecordMigration: true,
          showClosedReceiveRows: true,
        },
      }
    case 'receive-eyeball':
      return {
        component: MailReceiveConfirmModule,
        props: {
          sceneTypes: ['EYEBALL'] as SceneType[],
          defaultSceneType: 'EYEBALL' as SceneType,
          defaultCardType: 'EYEBALL',
          hideReceivedMailActions: true,
          showReceivedRecordMigration: true,
          showClosedReceiveRows: true,
        },
      }
    case 'exchange-request-review':
    case 'online-exchange-request-review':
      return { component: ExchangeRequestReviewModule }
    case 'online-bh6syx-import':
      return { component: OnlineImportDataModule }
    case 'offline-activity':
      return { component: OfflineActivityModule }
    case 'qso-query':
      return { component: QsoQueryModule }
    case 'card-query':
      return { component: CardQueryModule }
    case 'receive-record-query':
      return { component: ReceiveRecordQueryModule }
    case 'report-auditlog':
      return { component: ReportAuditlogModule }
    case 'audit-log':
      return { component: AuditLogModule }
    case 'address-management':
      return { component: AddressManagementModule }
    case 'bureau-management':
      return { component: BureauManagementModule }
    case 'equipment-catalog':
      return { component: EquipmentCatalogModule }
    case 'import-export':
      return { component: ImportExportModule }
    case 'log-sync':
      return { component: LogSyncModule }
    default:
      return {
        component: DefaultModulePlaceholder,
        props: {
          module: currentModule.value,
        },
      }
  }
})
</script>

<template>
  <QslModuleFrame :module="currentModule">
    <component :is="renderer.component" :key="currentModule.key" v-bind="renderer.props" />
  </QslModuleFrame>
</template>
