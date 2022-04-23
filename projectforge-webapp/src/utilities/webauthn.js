/* eslint-disable */
const lookup = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
const reverseLookup = new Uint8Array(256);

for (let i = 0; i < lookup.length; i += 1) {
    reverseLookup[lookup.charCodeAt(i)] = i;
}

const decodeBase64url = (base64url) => {
    const base64urlLength = base64url.length;

    const placeHolderLength = base64url.charAt(base64urlLength - 2) === '=' ? 2 : base64url.charAt(base64urlLength - 1) === '=' ? 1 : 0;
    const bufferLength = (base64urlLength * 3 / 4) - placeHolderLength;

    const arrayBuffer = new ArrayBuffer(bufferLength);
    const uint8Array = new Uint8Array(arrayBuffer);

    let j = 0;
    for (let i = 0; i < base64urlLength; i += 4) {
        const tmp0 = reverseLookup[base64url.charCodeAt(i)];
        const tmp1 = reverseLookup[base64url.charCodeAt(i + 1)];
        const tmp2 = reverseLookup[base64url.charCodeAt(i + 2)];
        const tmp3 = reverseLookup[base64url.charCodeAt(i + 3)];

        uint8Array[j++] = (tmp0 << 2) | (tmp1 >> 4);
        uint8Array[j++] = ((tmp1 & 15) << 4) | (tmp2 >> 2);
        uint8Array[j++] = ((tmp2 & 3) << 6) | (tmp3 & 63);
    }

    return arrayBuffer;
}

export const convertPublicKeyCredentialRequestOptions = (registerData) => {
    registerData.publicKey.challenge = decodeBase64url(registerData.publicKey.challenge);
    registerData.publicKey.user.id = decodeBase64url(registerData.publicKey.user.id);
}
