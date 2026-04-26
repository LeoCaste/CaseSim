import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { UserContext } from '../../core/services/user-context';

@Component({
  selector: 'app-topbar',
  imports: [RouterLink],
  templateUrl: './topbar.html',
  styleUrl: './topbar.css'
})
export class Topbar {
  constructor(public userContext: UserContext) {}
}
