use std::ffi::{CStr, c_char};

#[must_use]
pub fn get_string(str_ptr: *const c_char) -> String {
    unsafe { CStr::from_ptr(str_ptr).to_string_lossy().into_owned() }
}
