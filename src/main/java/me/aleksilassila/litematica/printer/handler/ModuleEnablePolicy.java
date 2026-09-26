package me.aleksilassila.litematica.printer.handler;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.WorkingModeType;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;

final class ModuleEnablePolicy {
    private ModuleEnablePolicy() {
    }

    static boolean allows(FeatureModuleBase module) {
        if (!ConfigUtils.isEnable()) return false;
        if (module.printMode != null && module.enableConfig != null) {
            WorkingModeType mode = (WorkingModeType) Configs.Core.WORK_MODE.getOptionListValue();
            return switch (mode) {
                case SINGLE -> Configs.Core.WORK_MODE_TYPE.getOptionListValue().equals(module.printMode);
                case MULTI -> module.enableConfig.getBooleanValue();
            };
        }
        return module.enableConfig == null || module.enableConfig.getBooleanValue();
    }
}
