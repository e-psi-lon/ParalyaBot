from shared.utils import *


async def message_callback(self, interaction: discord.Interaction):
    if self.notif:
        message = f"@everyone\n {self.children[0].value}"
    else:
        message = self.children[0].value
    webhook = await get_webhook(self.bot, self.channel, "🔋")
    await webhook.send(message, username="ParalyaBTW", avatar_url="https:/")
    await interaction.response.send_message("Message envoyé !", ephemeral=True)
