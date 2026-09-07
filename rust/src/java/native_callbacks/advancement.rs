use std::collections::HashMap;

use crate::{
    java::native_callbacks::CALLBACK_CONTEXT,
    proto::patchbukkit::{
        advancement::{
            AdvancementProgressRequest, AdvancementProgressResponse, AwardCriterionRequest,
            AwardCriterionResponse, GetAdvancementInfoRequest, ListAdvancementsResponse,
            RevokeCriterionRequest, RevokeCriterionResponse,
        },
        common::EmptyRequest,
    },
};

/// Resolves a vanilla advancement id (e.g. `minecraft:story/root`) to the
/// static tree node. Unknown ids yield `None`.
fn find_advancement_node(
    id: &str,
) -> Option<&'static pumpkin_data::advancement_data::AdvancementNode> {
    let (namespace, path) = id.split_once(':').unwrap_or(("minecraft", id));
    let identifier =
        pumpkin_util::identifier::Identifier::new(namespace.to_string(), path.to_string()).ok()?;
    pumpkin_data::ADVANCEMENT_TREE.get_node_from_id(&identifier)
}

fn find_advancement(id: &str) -> Option<&'static pumpkin_data::Advancement> {
    find_advancement_node(id).map(|node| node.value)
}

/// Fills the static metadata half of a progress response (criteria,
/// requirements, tree links, display data).
fn fill_metadata(
    response: &mut AdvancementProgressResponse,
    node: &'static pumpkin_data::advancement_data::AdvancementNode,
) {
    let advancement = node.value;
    response.all_criteria = advancement.criteria.iter().map(|c| c.to_string()).collect();
    response.requirement_groups = advancement
        .requirements
        .iter()
        .map(
            |group| crate::proto::patchbukkit::advancement::CriterionGroup {
                criteria: group.iter().map(|c| c.to_string()).collect(),
            },
        )
        .collect();
    response.parent_id = node
        .parent
        .and_then(|idx| pumpkin_data::ADVANCEMENT_TREE.get_node_from_idx(idx))
        .map_or_else(String::new, |parent| parent.value.id.to_string());
    response.children_ids = node
        .children
        .iter()
        .filter_map(|idx| pumpkin_data::ADVANCEMENT_TREE.get_node_from_idx(*idx))
        .map(|child| child.value.id.to_string())
        .collect();
    if let Some(display) = advancement.display {
        response.has_display = true;
        response.display_title_key = display.title.to_string();
        response.display_description_key = display.description.to_string();
        response.display_icon_id = display.item_icon.item.registry_key.to_string();
        response.display_icon_count = i32::from(display.item_icon.item_count);
        response.display_frame = match display.frame_type {
            pumpkin_data::advancement_data::FrameType::Task => "TASK",
            pumpkin_data::advancement_data::FrameType::Goal => "GOAL",
            pumpkin_data::advancement_data::FrameType::Challenge => "CHALLENGE",
        }
        .to_string();
        response.display_background = display.background_texture.unwrap_or_default().to_string();
        response.display_show_toast = display.show_toast;
        response.display_announce_to_chat = display.announce_to_chat;
        response.display_hidden = display.hidden;
    }
}

fn empty_response() -> AdvancementProgressResponse {
    AdvancementProgressResponse {
        exists: false,
        done: false,
        awarded_criteria: Vec::new(),
        remaining_criteria: Vec::new(),
        award_dates_millis: HashMap::new(),
        all_criteria: Vec::new(),
        requirement_groups: Vec::new(),
        parent_id: String::new(),
        children_ids: Vec::new(),
        has_display: false,
        display_title_key: String::new(),
        display_description_key: String::new(),
        display_icon_id: String::new(),
        display_icon_count: 0,
        display_frame: String::new(),
        display_background: String::new(),
        display_show_toast: false,
        display_announce_to_chat: false,
        display_hidden: false,
    }
}

pub fn ffi_native_bridge_get_advancement_progress_impl(
    request: AdvancementProgressRequest,
) -> Option<AdvancementProgressResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.player_uuid.as_ref()?.value;
    let player_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;
    let player = ctx.plugin_context.server.get_player_by_uuid(player_uuid)?;
    let Some(node) = find_advancement_node(&request.advancement_id) else {
        return Some(empty_response());
    };
    let advancement = node.value;

    let done = player.has_advancement(advancement);
    let mut response = AdvancementProgressResponse {
        exists: true,
        done,
        awarded_criteria: Vec::new(),
        remaining_criteria: Vec::new(),
        award_dates_millis: HashMap::new(),
        all_criteria: Vec::new(),
        requirement_groups: Vec::new(),
        parent_id: String::new(),
        children_ids: Vec::new(),
        has_display: false,
        display_title_key: String::new(),
        display_description_key: String::new(),
        display_icon_id: String::new(),
        display_icon_count: 0,
        display_frame: String::new(),
        display_background: String::new(),
        display_show_toast: false,
        display_announce_to_chat: false,
        display_hidden: false,
    };
    fill_metadata(&mut response, node);
    if let Ok(guard) = player.advancements.try_lock()
        && let Some(progress) = guard.progress.map.get(advancement)
    {
        response.awarded_criteria = progress
            .get_completed_criteria()
            .map(|c| c.to_string())
            .collect();
        response.remaining_criteria = progress
            .get_remaining_criteria()
            .map(|c| c.to_string())
            .collect();
        for (name, granted) in &progress.criteria {
            if let Some(when) = granted.0
                && let Ok(millis) = when
                    .duration_since(std::time::UNIX_EPOCH)
                    .map(|d| d.as_millis() as i64)
            {
                response.award_dates_millis.insert(name.to_string(), millis);
            }
        }
    }

    Some(response)
}

pub fn ffi_native_bridge_get_advancement_info_impl(
    request: GetAdvancementInfoRequest,
) -> Option<AdvancementProgressResponse> {
    let Some(node) = find_advancement_node(&request.advancement_id) else {
        return Some(empty_response());
    };
    let mut response = empty_response();
    response.exists = true;
    fill_metadata(&mut response, node);
    Some(response)
}

pub fn ffi_native_bridge_award_advancement_criterion_impl(
    request: AwardCriterionRequest,
) -> Option<AwardCriterionResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.player_uuid.as_ref()?.value;
    let player_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;
    let advancement = find_advancement(&request.advancement_id)?;
    // Only real criteria can be awarded; anything else is a no-op.
    if !advancement
        .criteria
        .iter()
        .any(|existing| *existing == request.criterion)
    {
        return Some(AwardCriterionResponse { awarded: false });
    }
    let player = ctx.plugin_context.server.get_player_by_uuid(player_uuid)?;
    player.trigger_advancement_criterion(advancement, &request.criterion);
    // The criterion counts as awarded when it shows up afterwards (it may
    // already have been awarded before this call, which still returns true).
    let awarded = player_awarded_criteria(&player, advancement).contains(&request.criterion);
    Some(AwardCriterionResponse { awarded })
}

pub fn ffi_native_bridge_revoke_advancement_criterion_impl(
    request: RevokeCriterionRequest,
) -> Option<RevokeCriterionResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.player_uuid.as_ref()?.value;
    let player_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;
    let advancement = find_advancement(&request.advancement_id)?;
    let player = ctx.plugin_context.server.get_player_by_uuid(player_uuid)?;
    let revoked = player
        .advancements
        .try_lock()
        .ok()
        .map(|mut guard| guard.revoke(advancement, &request.criterion))
        .unwrap_or(false);
    Some(RevokeCriterionResponse { revoked })
}

pub fn ffi_native_bridge_list_advancements_impl(
    _request: EmptyRequest,
) -> Option<ListAdvancementsResponse> {
    let advancement_ids = pumpkin_data::ADVANCEMENT_TREE
        .nodes_vector
        .iter()
        .map(|node| node.value.id.to_string())
        .collect();
    Some(ListAdvancementsResponse { advancement_ids })
}

fn player_awarded_criteria(
    player: &std::sync::Arc<pumpkin::entity::player::Player>,
    advancement: &'static pumpkin_data::Advancement,
) -> Vec<String> {
    player
        .advancements
        .try_lock()
        .ok()
        .and_then(|guard| {
            guard
                .progress
                .map
                .get(advancement)
                .map(|p| p.get_completed_criteria().map(|c| c.to_string()).collect())
        })
        .unwrap_or_default()
}
