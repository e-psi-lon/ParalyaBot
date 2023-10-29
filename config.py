import discord
from discord import ButtonStyle, Emoji, PartialEmoji, SelectOption, ChannelType, ComponentType
from discord.ui import Item
from utils import *

class GoBackButton(discord.ui.Button):
    def __init__(self, ctx, *, style: ButtonStyle = ButtonStyle.secondary, label: str | None = None, disabled: bool = False, custom_id: str | None = None, url: str | None = None, emoji: str | Emoji | PartialEmoji | None = None, row: int | None = None):
        super().__init__(style=style, label=label, disabled=disabled, custom_id=custom_id, url=url, emoji=emoji, row=row)
        self.label = "Retour"
        self.style = discord.ButtonStyle.secondary
        self.emoji = "↩️"
        self.ctx = ctx
    async def callback(self, interaction: discord.Interaction):
        await interaction.message.edit(view=ConfigView(self.ctx), embed=EMBED_PAGE_DEFAULT)

class CloseButton(discord.ui.Button):
    def __init__(self, *, style: ButtonStyle = ButtonStyle.secondary, label: str | None = None, disabled: bool = False, custom_id: str | None = None, url: str | None = None, emoji: str | Emoji | PartialEmoji | None = None, row: int | None = None):
        super().__init__(style=style, label=label, disabled=disabled, custom_id=custom_id, url=url, emoji=emoji, row=row)
        self.label = "Terminer"
        self.style = discord.ButtonStyle.green
        self.emoji = "✅"
    async def callback(self, interaction: discord.Interaction):
        await interaction.message.delete()

class EscapeSequenceButton(discord.ui.Button):
    def __init__(self, ctx, anonymous_channel, user_channel, *, style: ButtonStyle = ButtonStyle.secondary, label: str | None = None, disabled: bool = False, custom_id: str | None = None, url: str | None = None, emoji: str | Emoji | PartialEmoji | None = None, row: int | None = None):
        super().__init__(style=style, label=label, disabled=disabled, custom_id=custom_id, url=url, emoji=emoji, row=row)
        self.label = "Séquence d'échappement"
        self.style = discord.ButtonStyle.secondary
        self.emoji = "🔠"
        self.anonymous_channel = anonymous_channel
        self.user_channel = user_channel
        self.list_index = get_list_index(get_anonymous(ctx.guild.id), self.user_channel, self.anonymous_channel)
        self.ctx = ctx
    async def callback(self, interaction: discord.Interaction):
        # On affiche un modal qui demande la séquence d'échappement en précisant la précédente si elle existe
        await interaction.response.send_modal(AskEscapeSequence(self.ctx, self.anonymous_channel, self.user_channel, title="Séquence d'échappement"))
        

class AskEscapeSequence(discord.ui.Modal):
    def __init__(self, ctx, anonymous_channel, user_channel, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.ctx: discord.ApplicationContext = ctx
        config = get_anonymous(ctx.guild.id)
        self.anonymous_channel = anonymous_channel
        self.user_channel = user_channel
        self.list_index = get_list_index(get_anonymous(ctx.guild.id), self.user_channel, self.anonymous_channel)
        self.add_item(discord.ui.InputText(label="Séquence d'échappement", placeholder="Séquence d'échappement", value=config[self.list_index][4] if config[self.list_index][4] is not None else "", max_length=10))
        self.view:ChannelConfigView

    async def callback(self, interaction: discord.Interaction):
        config = get_anonymous(interaction.guild.id)
        config[self.list_index][4] = self.children[0].value
        edit_anonymous(config, interaction.guild.id)
        await interaction.response.send_message("La séquence d'échappement a bien été modifiée", ephemeral=True)
        await interaction.message.edit(view=ConfigView(self.ctx), embed=EMBED_PAGE_DEFAULT)
    

class RemoveButton(discord.ui.Button):
    def __init__(self, ctx, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.ctx: discord.ApplicationContext = ctx
        self.label = "Supprimer"
        self.style = discord.ButtonStyle.danger
        self.emoji = "🗑️"
        self.view:ChannelConfigView

    async def callback(self, interaction: discord.Interaction):
        config = get_anonymous(interaction.guild.id)
        list_index = get_list_index(config, self.view.children[0].user_channel, self.view.children[1].anonymous_channel)
        if list_index == -1:
            await interaction.response.send_message("Erreur", ephemeral=True)
            return
        config.pop(list_index)
        edit_anonymous(config, interaction.guild.id)
        await interaction.response.send_message("Le duo a bien été supprimé", ephemeral=True)
        await interaction.message.edit(view=ConfigView(self.ctx), embed=EMBED_PAGE_DEFAULT)

class ChannelConfigView(discord.ui.View):
    def __init__(self, ctx, user_channel, anonymous_channel, *items: Item, timeout: float | None = 180, disable_on_timeout: bool = False):
        super().__init__(*items, timeout=timeout, disable_on_timeout=disable_on_timeout)
        self.children: list[UserChannel | AnonymousChannel | EditIgnoredUser | RemoveButton | GoBackButton | EscapeSequenceButton] = []
        self.ctx: discord.ApplicationContext = ctx
        self.add_item(UserChannel(ctx=self.ctx, user_channel=user_channel, anonymous_channel=anonymous_channel))
        self.add_item(AnonymousChannel(ctx=self.ctx, user_channel=user_channel, anonymous_channel=anonymous_channel))
        self.add_item(EditIgnoredUser(ctx=self.ctx, user_channel=user_channel, anonymous_channel=anonymous_channel))
        self.add_item(EscapeSequenceButton(ctx=self.ctx, user_channel=user_channel, anonymous_channel=anonymous_channel, row=4))
        self.add_item(RemoveButton(ctx=self.ctx, row=4))
        self.add_item(GoBackButton(ctx=self.ctx, row=4))

class ConfigView(discord.ui.View):
    def __init__(self, ctx, *items: Item, timeout: float | None = 180, disable_on_timeout: bool = False):
        super().__init__(*items, timeout=timeout, disable_on_timeout=disable_on_timeout)
        self.ctx: discord.ApplicationContext = ctx
        self.add_item(ConfiguredChannel(ctx=self.ctx))
        self.add_item(CloseButton())

class UserChannel(discord.ui.Select):
    def __init__(self, ctx, user_channel, anonymous_channel, select_type: ComponentType = ComponentType.channel_select, *, custom_id: str | None = None, placeholder: str | None = None, min_values: int = 1, max_values: int = 1, options: list[SelectOption] = None, channel_types: list[ChannelType] = None, disabled: bool = False, row: int | None = None) -> None:
        super().__init__(select_type, custom_id=custom_id, placeholder=placeholder, min_values=min_values, max_values=max_values, options=options, channel_types=channel_types, disabled=disabled, row=row)
        self.placeholder = "Salon d'envoi des messages"
        self.ctx: discord.ApplicationContext = ctx
        self.user_channel = user_channel
        self.anonymous_channel = anonymous_channel
        self.list_index = get_list_index(get_anonymous(ctx.guild.id), self.user_channel, self.anonymous_channel)
        self.channel_types = [discord.ChannelType.text]

    async def callback(self, interaction: discord.Interaction):
        config = get_anonymous(interaction.guild.id)
        config[self.list_index][0] = self.values[0].id
        self.user_channel = self.values[0].id
        edit_anonymous(config, interaction.guild.id)
        await interaction.message.edit(embed=EMBED_CHANNEL_CONFIG.set_field_at(0, name="Salon d'envoi des messages", value=self.values[0].mention if self.values[0] else 'Aucun salon sélectionné'))
        await interaction.response.defer(invisible=True)

class AnonymousChannel(discord.ui.Select):
    def __init__(self, ctx, user_channel, anonymous_channel, select_type: ComponentType = ComponentType.channel_select, *, custom_id: str | None = None, placeholder: str | None = None, min_values: int = 1, max_values: int = 1, options: list[SelectOption] = None, channel_types: list[ChannelType] = None, disabled: bool = False, row: int | None = None) -> None:
        super().__init__(select_type, custom_id=custom_id, placeholder=placeholder, min_values=min_values, max_values=max_values, options=options, channel_types=channel_types, disabled=disabled, row=row)
        self.placeholder = "Salon d'envoi des messages anonymes"
        self.user_channel = user_channel
        self.anonymous_channel = anonymous_channel
        self.list_index = get_list_index(get_anonymous(ctx.guild.id), self.user_channel, self.anonymous_channel)
        self.ctx: discord.ApplicationContext = ctx
        self.channel_types = [discord.ChannelType.text]


    async def callback(self, interaction: discord.Interaction):
        config = get_anonymous(interaction.guild.id)
        config[self.list_index][1] = self.values[0].id
        self.anonymous_channel = self.values[0].id
        edit_anonymous(config, interaction.guild.id)
        await interaction.message.edit(embed=EMBED_CHANNEL_CONFIG.set_field_at(1, name="Salon d'envoi des messages anonymes", value=self.values[0].mention if self.values[0] else 'Aucun salon sélectionné'))
        await interaction.response.defer(invisible=True)

class ConfiguredChannel(discord.ui.Select):
    def __init__(self, ctx, select_type: ComponentType = ComponentType.string_select, *, custom_id: str | None = None, placeholder: str | None = None, min_values: int = 1, max_values: int = 1, options: list[SelectOption] = None, channel_types: list[ChannelType] = None, disabled: bool = False, row: int | None = None) -> None:
        super().__init__(select_type, custom_id=custom_id, placeholder=placeholder, min_values=min_values, max_values=max_values, options=options, channel_types=channel_types, disabled=disabled, row=row)
        self.ctx: discord.ApplicationContext = ctx
        self.placeholder = "Choisissez un duo à modifier"
        options = [discord.SelectOption(label=f"{self.ctx.guild.get_channel(channel[0]).name if channel[0] else 'Non Configuré'} - {self.ctx.guild.get_channel(channel[1]).name if channel[1] else 'Non Configuré'}", value=f"{channel[0]}-{channel[1]}" if channel[0] is not None and channel[1] is not None else f"None-{channel[1]}" if channel[0] is None else f"{channel[0]}-None") for channel in get_anonymous(self.ctx.guild.id)]
        options.append(discord.SelectOption(label="Ajouter un duo", value="add"))
        self.options = options
        self.values: discord.SelectOption
    async def callback(self, interaction: discord.Interaction):
        if self.values[0] == "add":
            config = get_anonymous(self.ctx.guild.id)
            config.append([None, None, None, [], None])
            edit_anonymous(config, self.ctx.guild.id)
            await interaction.message.edit(view=ChannelConfigView(self.ctx, user_channel=None, anonymous_channel=None), embed=EMBED_CHANNEL_CONFIG.set_field_at(0, name="Salon d'envoi des messages", value="Aucun salon sélectionné").set_field_at(1, name="Salon d'envoi des messages anonymes", value="Aucun salon sélectionné"))
        else:
            names = []
            for channel_id in self.values[0].split("-"):
                if channel_id == "None":
                    names.append(None)
                else:
                    names.append(int(channel_id))
            if names[0] is None: 
                embed = EMBED_CHANNEL_CONFIG.set_field_at(0, name="Salon d'envoi des messages", value="Aucun salon sélectionné")
            else:
                embed = EMBED_CHANNEL_CONFIG.set_field_at(0, name="Salon d'envoi des messages", value=f"<#{names[0]}>")
            if names[1] is None:
                embed.set_field_at(1, name="Salon d'envoi des messages anonymes", value="Aucun salon sélectionné")
            else:
                embed.set_field_at(1, name="Salon d'envoi des messages anonymes", value=f"<#{names[1]}>")
            config = get_anonymous(interaction.guild.id)
            list_index = get_list_index(config, names[0], names[1])
            if list_index == -1:
                embed.set_field_at(2, name="Utilisateurs ignorés", value="Aucun utilisateur ignoré")
            else:
                embed.set_field_at(2, name="Utilisateurs ignorés", value=f"{', '.join([f'<@{user_id}>' for user_id in config[list_index][3]]) if config[list_index][3] else 'Aucun utilisateur ignoré'}")
            if config[list_index][4] is None:
                embed.set_field_at(3, name="Séquence d'échappement", value="Aucune séquence d'échappement")
            else:
                embed.set_field_at(3, name="Séquence d'échappement", value=config[list_index][4])
            await interaction.message.edit(view=ChannelConfigView(self.ctx, user_channel=names[0], anonymous_channel=names[1]), embed=embed)
            await interaction.response.defer(invisible=True)

class EditIgnoredUser(discord.ui.Select):
    def __init__(self, ctx: discord.ApplicationContext, user_channel, anonymous_channel, select_type: ComponentType = ComponentType.user_select, *, custom_id: str | None = None, placeholder: str | None = None, min_values: int = 1, max_values: int = 1, options: list[SelectOption] = None, channel_types: list[ChannelType] = None, disabled: bool = False, row: int | None = None) -> None:
        super().__init__(select_type, custom_id=custom_id, placeholder=placeholder, min_values=min_values, max_values=max_values, options=options, channel_types=channel_types, disabled=disabled, row=row)
        self.placeholder = "Choisissez un utilisateur à ajouter"
        self.max_values = len([user for user in ctx.guild.members]) if len([user for user in ctx.guild.members]) < 25 else 25
        self.user_channel = user_channel
        self.anonymous_channel = anonymous_channel
        self.list_index = get_list_index(get_anonymous(ctx.guild.id), self.user_channel, self.anonymous_channel)
        self.previous_values: list[discord.Member | None] = [ctx.guild.get_member(user_id) for user_id in get_anonymous(ctx.guild.id)[self.list_index][3]]
    
    
    async def callback(self, interaction: discord.Interaction):
        config = get_anonymous(interaction.guild.id)
        if len(config[self.list_index][3]) >= self.max_values and self.values[0].id not in config[self.list_index][3]:
            await interaction.response.send_message(f"Vous ne pouvez pas ajouter plus de {self.max_values} utilisateurs", ephemeral=True)
            return
        response = []
        for user in self.values:
            if user.bot:
                response.append(f"{user.mention} est un bot, il ne peut pas être ajouté")
                self.values.remove(user)
            elif user.id not in self.previous_values and len(self.previous_values) < len([user for user in self.values if not user.bot]) and user.id not in config[self.list_index][3]:
                config[self.list_index][3].append(user.id)
                response.append(f"{user.mention} a été ajouté")
            elif len(self.previous_values) > len([user for user in self.values if not user.bot]):
                for previous_user in self.previous_values:
                    if previous_user not in [user for user in self.values if not user.bot] and previous_user.id in config[self.list_index][3]:
                        config[self.list_index][3].remove(previous_user.id)
                        response.append(f"{previous_user.mention} a été supprimé")
        edit_anonymous(config, interaction.guild.id)
        await interaction.message.edit(embed=EMBED_CHANNEL_CONFIG.set_field_at(2, name="Utilisateurs ignorés", value=f"{', '.join([f'<@{user_id}>' for user_id in config[self.list_index][3]]) if config[self.list_index][3] else 'Aucun utilisateur ignoré'}")) 
        await interaction.response.send_message(embed=discord.Embed(title="Changements sur les utilisateurs ignorés", description="\n".join(response), color=discord.Color.blurple()), ephemeral=True)
        self.previous_values = [user for user in self.values if not user.bot]
