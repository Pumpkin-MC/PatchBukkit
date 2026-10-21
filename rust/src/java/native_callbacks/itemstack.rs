use std::borrow::Cow;
use std::collections::HashMap;

use pumpkin_data::Enchantment;
use pumpkin_data::data_component::DataComponent;
use pumpkin_data::data_component_impl::{
    CustomNameImpl, DamageImpl, EnchantmentsImpl, EquipmentSlot, LoreImpl, UnbreakableImpl,
};
use pumpkin_data::item::Item;
use pumpkin_data::item_stack::ItemStack as PumpkinItemStack;
use pumpkin_util::text::TextComponent;

use crate::{
    java::native_callbacks::utils::with_player,
    proto::patchbukkit::{
        common::Uuid,
        itemstack::{
            GetPlayerInventoryResponse, ItemStack as ProtoItemStack, SetPlayerEquipmentRequest,
            SetPlayerInventorySlotRequest, SetPlayerSelectedSlotRequest,
        },
    },
};

fn pumpkin_item_to_proto(stack: &PumpkinItemStack) -> ProtoItemStack {
    if stack.is_empty() {
        ProtoItemStack {
            r#type: "minecraft:air".to_string(),
            amount: 0,
            damage: 0,
            custom_name: String::new(),
            lore: Vec::new(),
            enchantments: HashMap::new(),
            unbreakable: false,
        }
    } else {
        let registry_key = stack.item.registry_key;
        let r#type = if registry_key.starts_with("minecraft:") {
            registry_key.to_string()
        } else {
            format!("minecraft:{registry_key}")
        };
        let damage = stack
            .get_data_component::<DamageImpl>()
            .map_or(0, |d| d.damage.max(0) as u32);
        let custom_name = stack
            .get_data_component::<CustomNameImpl>()
            .map(|c| c.name.clone().get_text())
            .unwrap_or_default();
        let lore = stack
            .get_data_component::<LoreImpl>()
            .map(|l| l.lines.iter().map(|line| line.clone().get_text()).collect())
            .unwrap_or_default();
        let mut enchantments = HashMap::new();
        if let Some(enc_impl) = stack.get_data_component::<EnchantmentsImpl>() {
            for (enc, lvl) in enc_impl.enchantment.iter() {
                let key = if enc.name.starts_with("minecraft:") {
                    enc.name.to_string()
                } else {
                    format!("minecraft:{}", enc.name)
                };
                enchantments.insert(key, (*lvl).max(1) as u32);
            }
        }
        let unbreakable = stack.get_data_component::<UnbreakableImpl>().is_some();

        ProtoItemStack {
            r#type,
            amount: u32::from(stack.item_count),
            damage,
            custom_name,
            lore,
            enchantments,
            unbreakable,
        }
    }
}

pub(crate) fn proto_item_to_pumpkin(proto: Option<&ProtoItemStack>) -> PumpkinItemStack {
    let Some(proto) = proto else {
        return PumpkinItemStack::EMPTY.clone();
    };
    if proto.amount == 0 || proto.r#type.is_empty() {
        return PumpkinItemStack::EMPTY.clone();
    }
    let key = proto
        .r#type
        .strip_prefix("minecraft:")
        .unwrap_or(&proto.r#type);
    if let Some(item) = Item::from_registry_key(key) {
        let mut stack = PumpkinItemStack::new(proto.amount as u8, item);
        if proto.damage > 0 {
            stack.set_data_component(DamageImpl {
                damage: proto.damage as i32,
            });
        }
        if !proto.custom_name.is_empty() {
            stack.set_data_component(CustomNameImpl {
                name: TextComponent::text(proto.custom_name.clone()),
            });
        }
        if !proto.lore.is_empty() {
            stack.set_data_component(LoreImpl {
                lines: proto
                    .lore
                    .iter()
                    .map(|line| TextComponent::text(line.clone()))
                    .collect(),
            });
        }
        if !proto.enchantments.is_empty() {
            let mut encs = Vec::new();
            for (enc_key, lvl) in &proto.enchantments {
                let clean = enc_key.strip_prefix("minecraft:").unwrap_or(enc_key);
                if let Some(enc) =
                    Enchantment::from_name(clean).or_else(|| Enchantment::from_name(enc_key))
                {
                    encs.push((enc, (*lvl).max(1) as i32));
                }
            }
            if !encs.is_empty() {
                stack
                    .patch
                    .retain(|(id, _)| *id != DataComponent::Enchantments);
                stack.patch.push((
                    DataComponent::Enchantments,
                    Some(Box::new(EnchantmentsImpl {
                        enchantment: Cow::Owned(encs),
                    })),
                ));
            }
        }
        if proto.unbreakable {
            stack.set_data_component(UnbreakableImpl);
        }
        stack
    } else {
        PumpkinItemStack::EMPTY.clone()
    }
}

pub fn ffi_native_bridge_get_player_inventory_impl(
    request: Uuid,
) -> Option<GetPlayerInventoryResponse> {
    with_player(Some(&request), |player| {
        let selected_slot = u32::from(player.inventory.get_selected_slot());

        let main_inventory = player
            .inventory
            .main_inventory
            .try_read()
            .map(|main_guard| main_guard.iter().map(pumpkin_item_to_proto).collect())
            .unwrap_or_default();

        let (off_hand, helmet, chestplate, leggings, boots) =
            if let Ok(eq_guard) = player.inventory.entity_equipment.try_lock() {
                (
                    pumpkin_item_to_proto(&eq_guard.get(&EquipmentSlot::OFF_HAND)),
                    pumpkin_item_to_proto(&eq_guard.get(&EquipmentSlot::HEAD)),
                    pumpkin_item_to_proto(&eq_guard.get(&EquipmentSlot::CHEST)),
                    pumpkin_item_to_proto(&eq_guard.get(&EquipmentSlot::LEGS)),
                    pumpkin_item_to_proto(&eq_guard.get(&EquipmentSlot::FEET)),
                )
            } else {
                (
                    pumpkin_item_to_proto(PumpkinItemStack::EMPTY),
                    pumpkin_item_to_proto(PumpkinItemStack::EMPTY),
                    pumpkin_item_to_proto(PumpkinItemStack::EMPTY),
                    pumpkin_item_to_proto(PumpkinItemStack::EMPTY),
                    pumpkin_item_to_proto(PumpkinItemStack::EMPTY),
                )
            };

        GetPlayerInventoryResponse {
            main_inventory,
            selected_slot,
            off_hand: Some(off_hand),
            helmet: Some(helmet),
            chestplate: Some(chestplate),
            leggings: Some(leggings),
            boots: Some(boots),
        }
    })
}

pub fn ffi_native_bridge_set_player_inventory_slot_impl(
    request: SetPlayerInventorySlotRequest,
) -> Option<()> {
    let slot = request.slot as usize;
    let pumpkin_item = proto_item_to_pumpkin(request.item.as_ref());

    with_player(request.uuid.as_ref(), |player| {
        if slot < 36 {
            let mut main_guard = player
                .inventory
                .main_inventory
                .write()
                .unwrap_or_else(std::sync::PoisonError::into_inner);
            main_guard[slot] = pumpkin_item;
        } else {
            let eq_slot = match slot {
                36 => Some(EquipmentSlot::FEET),
                37 => Some(EquipmentSlot::LEGS),
                38 => Some(EquipmentSlot::CHEST),
                39 => Some(EquipmentSlot::HEAD),
                40 => Some(EquipmentSlot::OFF_HAND),
                _ => None,
            };
            if let Some(eq_slot) = eq_slot {
                let mut eq_guard = player
                    .inventory
                    .entity_equipment
                    .lock()
                    .unwrap_or_else(std::sync::PoisonError::into_inner);
                eq_guard.put(&eq_slot, pumpkin_item);
            }
        }
        player.on_screen_handler_opened(&player.player_screen_handler);
    })
}

pub fn ffi_native_bridge_set_player_selected_slot_impl(
    request: SetPlayerSelectedSlotRequest,
) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        if request.slot < 9 {
            player.inventory.set_selected_slot(request.slot as u8);
        }
    })
}

pub fn ffi_native_bridge_set_player_equipment_impl(
    request: SetPlayerEquipmentRequest,
) -> Option<()> {
    let pumpkin_item = proto_item_to_pumpkin(request.item.as_ref());
    let slot_type = request.slot_type;

    with_player(request.uuid.as_ref(), |player| {
        match slot_type {
            0 => {
                // Main Hand
                player.inventory.set_held_item(pumpkin_item);
            }
            1 => {
                // Off Hand
                let mut eq = player
                    .inventory
                    .entity_equipment
                    .lock()
                    .unwrap_or_else(std::sync::PoisonError::into_inner);
                eq.put(&EquipmentSlot::OFF_HAND, pumpkin_item);
            }
            2 => {
                // Feet
                let mut eq = player
                    .inventory
                    .entity_equipment
                    .lock()
                    .unwrap_or_else(std::sync::PoisonError::into_inner);
                eq.put(&EquipmentSlot::FEET, pumpkin_item);
            }
            3 => {
                // Legs
                let mut eq = player
                    .inventory
                    .entity_equipment
                    .lock()
                    .unwrap_or_else(std::sync::PoisonError::into_inner);
                eq.put(&EquipmentSlot::LEGS, pumpkin_item);
            }
            4 => {
                // Chest
                let mut eq = player
                    .inventory
                    .entity_equipment
                    .lock()
                    .unwrap_or_else(std::sync::PoisonError::into_inner);
                eq.put(&EquipmentSlot::CHEST, pumpkin_item);
            }
            5 => {
                // Head
                let mut eq = player
                    .inventory
                    .entity_equipment
                    .lock()
                    .unwrap_or_else(std::sync::PoisonError::into_inner);
                eq.put(&EquipmentSlot::HEAD, pumpkin_item);
            }
            _ => {}
        }
        player.on_screen_handler_opened(&player.player_screen_handler);
    })
}

pub fn ffi_native_bridge_clear_player_inventory_impl(request: Uuid) -> Option<()> {
    with_player(Some(&request), |player| {
        {
            let mut main_guard = player
                .inventory
                .main_inventory
                .write()
                .unwrap_or_else(std::sync::PoisonError::into_inner);
            main_guard.fill_with(|| PumpkinItemStack::EMPTY.clone());
            let mut eq_guard = player
                .inventory
                .entity_equipment
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner);
            eq_guard.clear();
        }
        player.on_screen_handler_opened(&player.player_screen_handler);
    })
}
