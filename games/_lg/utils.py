from shared.utils import *


async def message_callback(self, interaction: discord.Interaction):
    message = f"━━━━━━━━━━━━━━━━━━\n🐺 LGNotifications ¦ {self.children[0].value}\n━━━━━━━━━━━━━━━━━━"
    errors = []
    for member in self.members:
        try:
            if member.bot:
                continue
            await member.send(message)
        except discord.Forbidden:
            errors.append(member)
    if errors:
        await interaction.response.send_message(f"Impossible d'envoyer le message à {', '.join([member.mention for member in errors])}.",
                                                ephemeral=True)
    else:
        await interaction.response.send_message("Message envoyé !", ephemeral=True)
