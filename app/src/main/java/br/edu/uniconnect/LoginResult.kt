package br.edu.uniconnect

/** Shared, credential-free result handling for foreground and background login. */
object LoginResult {
    fun message(result: String?): String = when (result) {
        "\"invalid-fields\"" -> "O formulário não validou os campos. Confira o usuário e a senha."
        "\"wrong-origin\"", "\"wrong-action\"" -> "O destino do formulário não é o portal autorizado. Envio bloqueado."
        "\"unsupported\"" -> "Não foi possível reconhecer o formulário de login. Atualize o app ou use o portal oficial."
        "\"script-error\"" -> "Falha ao preparar o formulário no aplicativo (script-error). Nenhuma confirmação de envio."
        "\"submit-error\"" -> "Falha ao acionar o botão do portal (submit-error). Nenhuma repetição automática será feita."
        else -> "O formulário não pôde ser executado no aplicativo (sem resultado). Isso não confirma recusa de senha."
    }
}
