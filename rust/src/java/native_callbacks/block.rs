use pumpkin_util::math::position::BlockPos;

use crate::{
    java::native_callbacks::CALLBACK_CONTEXT,
    proto::patchbukkit::block::{GetBlockRequest, GetBlockResponse},
};

pub fn ffi_native_bridge_get_block_impl(request: GetBlockRequest) -> Option<GetBlockResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let world = request.world?;
    let world_uuid = world.uuid?.value;
    let world_uuid = uuid::Uuid::parse_str(&world_uuid).ok()?;

    let server = ctx.plugin_context.server.clone();
    let block_key = tokio::task::block_in_place(|| {
        ctx.runtime.block_on(async move {
            let world = server
                .worlds
                .load()
                .iter()
                .find(|world| world.uuid == world_uuid)?
                .clone();
            let pos = BlockPos::new(request.x, request.y, request.z);
            let block = world.get_block(&pos).await;
            Some(format!("minecraft:{}", block.name))
        })
    })?;

    Some(GetBlockResponse { block_key })
}
