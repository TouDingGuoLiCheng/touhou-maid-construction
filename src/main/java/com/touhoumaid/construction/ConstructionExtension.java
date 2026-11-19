package com.touhoumaid.construction;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.touhoumaid.construction.task.TaskConstruction;

@LittleMaidExtension
public class ConstructionExtension implements ILittleMaid {
    @Override
    public void addMaidTask(TaskManager manager) {
        manager.add(new TaskConstruction());
    }
}
