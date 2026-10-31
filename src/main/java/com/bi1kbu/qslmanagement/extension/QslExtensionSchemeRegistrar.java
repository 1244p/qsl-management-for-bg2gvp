package com.bi1kbu.qslmanagement.extension;

import com.bi1kbu.qslmanagement.extension.model.AddressBookEntry;
import com.bi1kbu.qslmanagement.extension.model.BureauEntry;
import com.bi1kbu.qslmanagement.extension.model.CardRecord;
import com.bi1kbu.qslmanagement.extension.model.EquipmentCatalogEntry;
import com.bi1kbu.qslmanagement.extension.model.ExchangeRequest;
import com.bi1kbu.qslmanagement.extension.model.ImportExportJob;
import com.bi1kbu.qslmanagement.extension.model.LogSyncJob;
import com.bi1kbu.qslmanagement.extension.model.LogSyncProfile;
import com.bi1kbu.qslmanagement.extension.model.OfflineExchangeCard;
import com.bi1kbu.qslmanagement.extension.model.OfflineActivity;
import com.bi1kbu.qslmanagement.extension.model.QslAuditLog;
import com.bi1kbu.qslmanagement.extension.model.QslCardRequest;
import com.bi1kbu.qslmanagement.extension.model.QslCardRequestQsoReservation;
import com.bi1kbu.qslmanagement.extension.model.QslMigrationState;
import com.bi1kbu.qslmanagement.extension.model.QsoRecord;
import com.bi1kbu.qslmanagement.extension.model.ReceiveRecord;
import com.bi1kbu.qslmanagement.extension.model.StationCard;
import com.bi1kbu.qslmanagement.extension.model.StationEquipment;
import com.bi1kbu.qslmanagement.extension.model.StationProfile;
import com.bi1kbu.qslmanagement.extension.model.SystemSetting;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Extension;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;

@Component
public class QslExtensionSchemeRegistrar {

    public QslExtensionSchemeRegistrar(SchemeManager schemeManager) {
        registerWithNameIndex(schemeManager, SystemSetting.class);
        registerWithNameIndex(schemeManager, StationProfile.class);
        registerWithNameIndex(schemeManager, StationEquipment.class);
        registerWithNameIndex(schemeManager, StationCard.class);
        registerWithNameIndex(schemeManager, QsoRecord.class);
        registerWithNameIndex(schemeManager, CardRecord.class);
        registerWithNameIndex(schemeManager, OfflineExchangeCard.class);
        registerWithNameIndex(schemeManager, ReceiveRecord.class);
        registerWithNameIndex(schemeManager, ExchangeRequest.class);
        registerWithNameIndex(schemeManager, OfflineActivity.class);
        registerWithNameIndex(schemeManager, AddressBookEntry.class);
        registerWithNameIndex(schemeManager, BureauEntry.class);
        registerWithNameIndex(schemeManager, EquipmentCatalogEntry.class);
        registerWithNameIndex(schemeManager, QslAuditLog.class);
        registerWithNameIndex(schemeManager, QslCardRequest.class);
        registerWithNameIndex(schemeManager, QslCardRequestQsoReservation.class);
        registerWithNameIndex(schemeManager, ImportExportJob.class);
        registerWithNameIndex(schemeManager, LogSyncProfile.class);
        registerWithNameIndex(schemeManager, LogSyncJob.class);
        registerWithNameIndex(schemeManager, QslMigrationState.class);
    }

    private <E extends Extension> void registerWithNameIndex(SchemeManager schemeManager, Class<E> type) {
        try {
            schemeManager.unregister(schemeManager.get(type));
        } catch (Exception ignored) {
            // 忽略未注册场景，继续注册带索引的新 scheme。
        }
        schemeManager.register(type, specs -> specs.add(
            IndexSpecs.<E, String>single("name", String.class)
                .unique(true)
                .nullable(false)
                .indexFunc(extension -> extension.getMetadata().getName())
        ));
    }
}
